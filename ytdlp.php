<?php
ini_set('display_errors', 1);
ini_set('display_startup_errors', 1);
error_reporting(E_ALL);
$baseTmpDir = __DIR__ . '/tmp';
$ffmpegBin = '/data/data/com.termux/files/usr/bin/ffmpeg';
$ytdlpBin = '/data/data/com.termux/files/usr/bin/yt-dlp';
$fps = 10;
$vRate = '50k';

foreach (glob($baseTmpDir . '/ytdlp_job_*', GLOB_ONLYDIR) as $dir) {
    if (time() - filemtime($dir) > 1800) {
        array_map('unlink', glob("$dir/*"));
        @rmdir($dir);
    }
}

$listTitle = 'Search Results:';

function ytFlatList($target, $extra = '', $kind = 'video') {
    global $ytdlpBin;
    $cmd = "$ytdlpBin --cookies cookies.txt " . escapeshellarg($target) . " --flat-playlist $extra --dump-json 2>&1";
    $raw = shell_exec($cmd);
    $items = [];
    foreach (explode("\n", trim((string)$raw)) as $line) {
        $d = json_decode(trim($line), true);
        if (!isset($d['id'], $d['title'])) continue;
        $u = $d['url'] ?? '';
        if ($kind === 'channel' && !preg_match('~youtube\.com/(channel/|@|c/|user/)~', $u)) continue;
        if ($kind === 'video' && strpos($u, 'playlist?list=') !== false) { $kind2 = 'playlist'; } else { $kind2 = $kind; }
        $items[] = ['id' => $d['id'], 'title' => toLatin($d['title']), 'url' => $u, 'kind' => $kind2];
    }
    return [$items, $raw];
}

function ytdlpSearch($q) {
    list($chans, ) = ytFlatList(
        'https://www.youtube.com/results?search_query=' . urlencode($q) . '&sp=EgIQAg%253D%253D',
        '--playlist-end 5 --no-warnings',
        'channel'
    );
    list($videos, ) = ytFlatList('ytsearch30:' . $q, '--no-warnings');

    return array_merge($chans, $videos);
}

function toLatin($s) {
    if (function_exists('transliterator_transliterate')) {
        $r = transliterator_transliterate('Any-Latin; Latin-ASCII; [^\u0020-\u007E] Remove', $s);
        if ($r !== false) $s = $r;
    }
    $s = preg_replace('/[^\x20-\x7E]/', '', $s);
    return trim(preg_replace('/\s+/', ' ', $s));
}

function findSourceFile($jobDir, $type = 'video') {
    $urlFile = $jobDir . '/stream_url.txt';
    if (file_exists($urlFile) && filesize($urlFile) > 0) {
        $lines = array_values(array_filter(array_map('trim', file($urlFile))));
        if (!empty($lines)) {
            if ($type === 'audio' && count($lines) > 1) {
                return $lines[1];
            }
            return $lines[0];
        }
    }
    foreach (glob($jobDir . '/source.*') as $f) {
        if (is_file($f) && substr($f, -5) !== '.part' && !preg_match('/\.f\d+\./', $f)) {
            return $f;
        }
    }
    return null;
}

function startJobProcess($jobDir, $jobId, $format, $urlInput) {
    global $ffmpegBin, $ytdlpBin;
    $url = escapeshellarg($urlInput);
    $isStreamFmt = in_array($format, ['mjpeg_amr', 'mjpeg_wav', 'mjpeg_aac', 'mjpeg_aac_hf', 'audio_amr', 'audio_wav']);
    $isWav       = in_array($format, ['mjpeg_wav', 'audio_wav']);
    $isAudioOnly = in_array($format, ['audio_amr', 'audio_wav']);

    $outputExt = null;
    $ffmpegCmd = null;

    if ($format === 'mp3') {
        $outputExt = 'mp3';
        $ffmpegCmd = "nice -n 19 $ffmpegBin -threads 1 -i \"\$SRC\" -vn -acodec libmp3lame -ab 128k -y " . escapeshellarg($jobDir . '/output.mp3');
    } elseif ($format === 'mp4_mpeg4') {
        $outputExt = 'mp4';
        $ffmpegCmd = "nice -n 19 $ffmpegBin -threads 1 -i \"\$SRC\" -vcodec mpeg4 -s 176x144 -acodec aac -y " . escapeshellarg($jobDir . '/output.mp4');
    } elseif ($format === 'mp4_h264') {
        $outputExt = 'mp4';
        $ffmpegCmd = "nice -n 19 $ffmpegBin -threads 1 -i \"\$SRC\" -vcodec libx264 -crf 23 -preset ultrafast -pix_fmt yuv420p -acodec aac -y " . escapeshellarg($jobDir . '/output.mp4');
    } else {
        $outputExt = '3gp';
        $ffmpegCmd = "nice -n 19 $ffmpegBin -threads 1 -i \"\$SRC\" -vcodec h263 -s 176x144 -r 12 -acodec aac -ab 32k -y " . escapeshellarg($jobDir . '/output.3gp');
    }
    
        $vSel = 'bestvideo[height<=144][vcodec^=avc1]/bestvideo[height<=144]';
        $aSel = 'bestaudio[format_note*=default]/bestaudio[format_note!*=?dubbed]/bestaudio';

            $isCache = in_array($format, ['mjpeg_amr', 'audio_amr']);

    if ($isCache) {
        $ytFormat = $isAudioOnly ? $aSel : "($vSel)+($aSel)";
        $st = escapeshellarg($jobDir . '/status.json');
        $lg = escapeshellarg($jobDir . '/log.txt');
        $fmtJ = $format;
        $urlJ = addslashes($urlInput);

        $shScript = "$ytdlpBin --cookies cookies.txt --match-filter " . escapeshellarg('duration<=?7200') . " -f " . escapeshellarg($ytFormat) .
            " --merge-output-format mkv --ffmpeg-location $ffmpegBin -o " . escapeshellarg($jobDir . '/source.%(ext)s') .
            " " . $url . " > $lg 2>&1; " .
            "SRC=\$(ls " . $jobDir . "/source.* 2>/dev/null | grep -v '\\.part$' | grep -v '\\.f[0-9]*\\.' | head -n 1); " .
            "if [ -n \"\$SRC\" ]; then " .
            "  echo '{\"state\":\"completed\",\"format\":\"$fmtJ\",\"url\":\"$urlJ\"}' > $st; " .
            "elif grep -q 'does not pass filter' $lg; then " .
            "  echo '{\"state\":\"error\",\"msg\":\"Video is longer than 2 hour (not allowed for streaming)\",\"format\":\"$fmtJ\",\"url\":\"$urlJ\"}' > $st; " .
            "else " .
            "  echo '{\"state\":\"error\",\"msg\":\"yt-dlp download failed\",\"format\":\"$fmtJ\",\"url\":\"$urlJ\"}' > $st; " .
            "fi";
    } elseif ($isStreamFmt) {
        $ytFormat = $isAudioOnly ? $aSel : "($vSel)+($aSel)";

        $shScript = "$ytdlpBin --cookies cookies.txt --match-filter " . escapeshellarg('duration<=?7200') . " -f " . escapeshellarg($ytFormat) .
    " -g " . $url . " > " . escapeshellarg($jobDir . '/stream_url.txt') . " 2> " . escapeshellarg($jobDir . '/log.txt') . "; " .
    "if [ -s " . escapeshellarg($jobDir . '/stream_url.txt') . " ]; then " .
    "  echo '{\"state\":\"completed\",\"format\":\"" . $format . "\",\"url\":\"" . addslashes($urlInput) . "\"}' > " . escapeshellarg($jobDir . '/status.json') . "; " .
    "elif grep -q 'does not pass filter' " . escapeshellarg($jobDir . '/log.txt') . "; then " .
    "  echo '{\"state\":\"error\",\"msg\":\"Video is longer than 2 hour (not allowed for streaming)\",\"format\":\"" . $format . "\",\"url\":\"" . addslashes($urlInput) . "\"}' > " . escapeshellarg($jobDir . '/status.json') . "; " .
    "else " .
    "  echo '{\"state\":\"error\",\"msg\":\"Failed to fetch direct stream URL\",\"format\":\"" . $format . "\",\"url\":\"" . addslashes($urlInput) . "\"}' > " . escapeshellarg($jobDir . '/status.json') . "; " .
    "fi";
} else {
        $ytFormat = ($format === 'mp3') ? $aSel : "($vSel)+($aSel)";

        $ytdlpCmd = "$ytdlpBin --cookies cookies.txt --match-filter " . escapeshellarg('duration<=?7200') . " -f " . escapeshellarg($ytFormat) .
            " --merge-output-format mkv --ffmpeg-location $ffmpegBin -o " . escapeshellarg($jobDir . '/source.%(ext)s') .
            " " . $url . " > " . escapeshellarg($jobDir . '/log.txt') . " 2>&1; " .
            "SRC=\$(ls " . $jobDir . "/source.* 2>/dev/null | grep -v '\\.part$' | grep -v '\\.f[0-9]*\\.' | head -n 1); ";

        $shScript = $ytdlpCmd .
            "if [ -n \"\$SRC\" ]; then " .
            "  echo '{\"state\":\"running\",\"step\":\"Converting video with ffmpeg...\",\"format\":\"" . $format . "\",\"url\":\"" . addslashes($urlInput) . "\"}' > " . escapeshellarg($jobDir . '/status.json') . "; " .
            $ffmpegCmd . " >> " . escapeshellarg($jobDir . '/log.txt') . " 2>&1; " .
            "  if [ -f " . escapeshellarg($jobDir . '/output.' . $outputExt) . " ]; then " .
            "    echo '{\"state\":\"completed\",\"format\":\"" . $format . "\",\"url\":\"" . addslashes($urlInput) . "\"}' > " . escapeshellarg($jobDir . '/status.json') . "; " .
            "  else " .
            "    echo '{\"state\":\"error\",\"msg\":\"FFmpeg conversion failed\",\"format\":\"" . $format . "\",\"url\":\"" . addslashes($urlInput) . "\"}' > " . escapeshellarg($jobDir . '/status.json') . "; " .
            "  fi; " .
            "elif grep -q 'does not pass filter' " . escapeshellarg($jobDir . '/log.txt') . "; then " .
            "  echo '{\"state\":\"error\",\"msg\":\"Video is longer than 2 hours (not allowed)\",\"format\":\"" . $format . "\",\"url\":\"" . addslashes($urlInput) . "\"}' > " . escapeshellarg($jobDir . '/status.json') . "; " .
            "else " .
            "  echo '{\"state\":\"error\",\"msg\":\"yt-dlp download failed\",\"format\":\"" . $format . "\",\"url\":\"" . addslashes($urlInput) . "\"}' > " . escapeshellarg($jobDir . '/status.json') . "; " .
            "fi";
    }

    $cmd = "nohup sh -c " . escapeshellarg($shScript) . " > /dev/null 2>&1 &";
    exec($cmd);
    if ($isStreamFmt) {
    $subCmd = "$ytdlpBin --cookies cookies.txt --skip-download --write-subs --write-auto-subs --sub-langs " . escapeshellarg('en') .
    " --sub-format vtt --convert-subs srt --ffmpeg-location $ffmpegBin --retries 5 --extractor-retries 5" .
    " -o " . escapeshellarg($jobDir . '/sub') . " " . $url .
    " > " . escapeshellarg($jobDir . '/sub_log.txt') . " 2>&1";
    exec("nohup sh -c " . escapeshellarg($subCmd) . " > /dev/null 2>&1 &");
}
}

if (isset($_GET['action']) && $_GET['action'] === 'chthumb' && !empty($_GET['u'])) {
    $u = rtrim($_GET['u'], '/');
    if (!preg_match('~^https?://(www\.)?youtube\.com/(channel/|@|c/|user/)~', $u)) {
        header('HTTP/1.0 400 Bad Request');
        exit;
    }
    $u = preg_replace('~/(videos|shorts|streams|live|featured|releases|playlists)$~', '', $u);
    $cacheDir = $baseTmpDir . '/thumbcache';
    if (!file_exists($cacheDir)) mkdir($cacheDir, 0755, true);
    $cf = $cacheDir . '/' . md5($u) . '.txt';
    $img = file_exists($cf) ? trim(file_get_contents($cf)) : '';
    if ($img === '') {
        $ctx = stream_context_create(['http' => [
            'timeout' => 8,
            'header'  => "User-Agent: Mozilla/5.0\r\nCookie: CONSENT=YES+1; SOCS=CAI\r\nAccept-Language: en\r\n"
        ]]);
        $html = @file_get_contents($u, false, $ctx);
        if ($html && preg_match('~<meta property="og:image" content="([^"]+)"~', $html, $m)) {
            $img = html_entity_decode($m[1]);
            file_put_contents($cf, $img);
        }
    }
        if ($img !== '') {
        if (isset($_GET['s'])) {
            $s = max(16, min(96, (int)$_GET['s']));
            $sc = $cacheDir . '/' . md5($u) . '_' . $s . '.jpg';
            if (!file_exists($sc)) {
                $ctx2 = stream_context_create(['http' => ['timeout' => 8, 'header' => "User-Agent: Mozilla/5.0\r\n"]]);
                $small = preg_replace('~=s\d+~', '=s96', $img);
                $bin = @file_get_contents($small, false, $ctx2);
                if (!$bin) $bin = @file_get_contents($img, false, $ctx2);
                $src = $bin ? @imagecreatefromstring($bin) : false;
                if ($src) {
                    $sw = imagesx($src); $sh = imagesy($src);
                    $side = min($sw, $sh);
                    $dst = imagecreatetruecolor($s, $s);
                    imagecopyresampled($dst, $src, 0, 0, intval(($sw - $side) / 2), intval(($sh - $side) / 2), $s, $s, $side, $side);
                    imagejpeg($dst, $sc, 60);
                    imagedestroy($src);
                    imagedestroy($dst);
                }
            }
            while (ob_get_level()) ob_end_clean();
            if (file_exists($sc)) {
                header('Content-Type: image/jpeg');
                header('Cache-Control: max-age=86400');
                header('Content-Length: ' . filesize($sc));
                readfile($sc);
            } else {
                header('HTTP/1.0 404 Not Found');
            }
            exit;
        }
    } else {
        header('HTTP/1.0 404 Not Found');
    }
    exit;
}

if (isset($_GET['download'])) {
    $jobId = substr(preg_replace('/[^a-f0-9]/', '', $_GET['download']), 0, 12);
    $jobDir = $baseTmpDir . '/ytdlp_job_' . $jobId;
    $statusFile = $jobDir . '/status.json';
    $jobStatus = file_exists($statusFile) ? json_decode(file_get_contents($statusFile), true) : [];
    $format = $jobStatus['format'] ?? '3gp';
    $ext = ($format === 'mp3') ? 'mp3' : (($format === '3gp') ? '3gp' : 'mp4');
    $outputFile = $jobDir . '/output.' . $ext;
    if (file_exists($outputFile)) {
        while (ob_get_level()) { ob_end_clean(); }
        header('Content-Description: File Transfer');
        if ($format === 'mp3') {
            header('Content-Type: audio/mpeg');
            header('Content-Disposition: attachment; filename=audio.mp3');
        } elseif ($format === '3gp') {
            header('Content-Type: video/3gpp');
            header('Content-Disposition: attachment; filename=video.3gp');
        } else {
            header('Content-Type: video/mp4');
            header('Content-Disposition: attachment; filename=video.mp4');
        }
        header('Content-Length: ' . filesize($outputFile));
        readfile($outputFile);
        exit;
    }
}

if (isset($_GET['action']) && $_GET['action'] === 'rate' && isset($_GET['job'])) {
    $jobId = preg_replace('/[^a-f0-9]/', '', $_GET['job']);
    $l = max(0, min(5, (int)($_GET['l'] ?? 2)));
    file_put_contents($baseTmpDir . '/ytdlp_job_' . $jobId . '/rate.txt', $l);
    echo 'ok';
    exit;
}

if (isset($_GET['action']) && $_GET['action'] === 'stream' && isset($_GET['job'])) {
    $jobId = preg_replace('/[^a-f0-9]/', '', $_GET['job']);
    $jobDir = $baseTmpDir . '/ytdlp_job_' . $jobId;
    $srcFile = findSourceFile($jobDir, 'video');

    if (!$srcFile) {
        header("HTTP/1.0 404 Not Found");
        echo "Source video not found (still downloading, or job expired).";
        exit;
    }

    ignore_user_abort(true);
    set_time_limit(0);
    while (ob_get_level() > 0) {
        ob_end_flush();
    }
    ob_implicit_flush(true);

    header("Content-Type: multipart/x-mixed-replace; boundary=ffmpeg");
    header("Cache-Control: no-cache, private");
    header("Pragma: no-cache");

    $loopForever = isset($_GET['loop']) && intval($_GET['loop']) === 1;
    $loopParam = $loopForever ? '-stream_loop -1 ' : '';

    $stFile = $jobDir . '/status.json';
    $stData = file_exists($stFile) ? json_decode(file_get_contents($stFile), true) : [];
    $stFmt  = $stData['format'] ?? '';

    if ($stFmt === 'mjpeg_aac_hf') {
        $sFps  = 23;
        $sH    = 140;
        $vArgs = '-q:v 5';
    } elseif ($stFmt === 'mjpeg_aac') {
        $sFps  = $fps;
        $sH    = 96;
        $vArgs = '-q:v 5';
    } else {
        $sFps  = $fps;
        $sH    = 96;
        $tb    = '160k';
        $vArgs = "-b:v $tb -maxrate $tb -bufsize 40k";
    }

        $reconnectFlags = (strpos($srcFile, 'http') === 0) ? '-reconnect 1 -reconnect_streamed 1 -reconnect_delay_max 5 -fflags +genpts+discardcorrupt ' : '';

$levels = [[64,'48k'],[64,'128k'],[64,'160k'],[96,'48k'],[96,'128k'],[96,'160k']];
$maxLv = count($levels) - 1;
$rateFile = $jobDir . '/rate.txt';
$getLv = function() use ($rateFile, $maxLv) {
    return is_file($rateFile) ? max(0, min($maxLv, (int)file_get_contents($rateFile))) : 2;
};
    $sent = 0;
    $done = false;
    while (!$done && !connection_aborted()) {
        $lv = $getLv();
        if ($stFmt === 'mjpeg_amr') {
            $sH = $levels[$lv][0];
            $tb = $levels[$lv][1];
            $vArgs = "-b:v $tb -maxrate $tb -bufsize 40k";
        }
        $ss = $sent > 0 ? '-ss ' . round($sent / $sFps, 2) . ' ' : '';
        $frameCmd = sprintf(
            '%s %s%s-i %s -an -vf "fps=%d,scale=-2:%d:flags=fast_bilinear" ' .
            '-c:v mjpeg -huffman optimal %s -f mpjpeg - 2>/dev/null',
            $ffmpegBin, $reconnectFlags, $loopParam . $ss,
            escapeshellarg($srcFile), $sFps, $sH, $vArgs
        );
        $handle = popen($frameCmd, 'r');
        if (!$handle) break;
        $changed = false;
        while (!feof($handle)) {
            if (connection_aborted()) { $done = true; break; }

    $buf = fread($handle, 8192);
            if ($buf === false || $buf === '') continue;
            echo $buf;
            flush();
            $sent += substr_count($buf, 'image/jpeg');
            if ($stFmt === 'mjpeg_amr' && $getLv() !== $lv) { $changed = true; break; }
        }
        pclose($handle);
        if (!$changed) $done = true;
    }
    exit;
}

if (isset($_GET['action']) && $_GET['action'] === 'audio' && isset($_GET['job'])) {
    $jobId = preg_replace('/[^a-f0-9]/', '', $_GET['job']);
    $jobDir = $baseTmpDir . '/ytdlp_job_' . $jobId;
    $srcFile = findSourceFile($jobDir, 'audio');

    if (!$srcFile) {
        header("HTTP/1.0 404 Not Found");
        echo "Audio source not found.";
        exit;
    }

    $statusFile = $jobDir . '/status.json';
    $jobStatus = file_exists($statusFile) ? json_decode(file_get_contents($statusFile), true) : [];
    $format = $jobStatus['format'] ?? 'audio_wav';
    $isWav = in_array($format, ['mjpeg_wav', 'audio_wav']);

    ignore_user_abort(true);
    set_time_limit(0);
    while (ob_get_level() > 0) { ob_end_flush(); }
    ob_implicit_flush(true);

if ($format === 'mjpeg_aac_hf') {
    header("Content-Type: audio/aac");
    $audioCodec = "-ar 22050 -ac 1 -acodec aac -b:a 32k -f adts";
} elseif ($format === 'mjpeg_aac') {
    header("Content-Type: audio/aac");
    $audioCodec = "-ar 22050 -ac 1 -acodec aac -b:a 32k -f adts";
} elseif ($isWav) {
    header("Content-Type: audio/wav");
    $audioCodec = "-ar 8000 -ac 1 -acodec pcm_u8 -f wav";
} else {
    header("Content-Type: audio/amr");
    $audioCodec = "-ar 8000 -ac 1 -acodec libopencore_amrnb -b:a 7200 -f amr";
}
header("Cache-Control: no-cache, private");

$reconnectFlags = (strpos($srcFile, 'http') === 0) ? '-reconnect 1 -reconnect_streamed 1 -reconnect_delay_max 5 ' : '';

 $cmd = sprintf(
    '%s %s-i %s -vn -af "volume=10.0,alimiter=limit=0.9" %s - 2>/dev/null',
    $ffmpegBin,
    $reconnectFlags,
    escapeshellarg($srcFile),
    $audioCodec
); 

    $handle = popen($cmd, 'r');
    if ($handle) {
        while (!feof($handle)) {
            if (connection_aborted()) break;
            echo fread($handle, 8192);
            flush();
        }
        pclose($handle);
    }
    exit;
}

$jobStatus = null;
$currentJobId = '';
$isSearchGet = (isset($_GET['action']) && $_GET['action'] === 'search');

if (isset($_GET['action']) && $_GET['action'] === 'refresh' && isset($_GET['job'])) {
    $jobId = preg_replace('/[^a-f0-9]/', '', $_GET['job']);
    header("Location: " . basename($_SERVER['PHP_SELF']) . "?job=" . $jobId);
    exit;
}

if (!$isSearchGet) {
    if (isset($_GET['job'])) {
        $currentJobId = preg_replace('/[^a-f0-9]/', '', $_GET['job']);
        $jobDir = $baseTmpDir . '/ytdlp_job_' . $currentJobId;
        $statusFile = $jobDir . '/status.json';
        if (file_exists($statusFile)) {
            $jobStatus = json_decode(file_get_contents($statusFile), true);
        }
    }
}


$output = "";
$searchResults = [];

$incomingUrl = $_POST['url'] ?? $_GET['url'] ?? '';
$format = $_POST['format'] ?? $_GET['format'] ?? '3gp';
$validFormats = ['3gp', 'mp4_mpeg4', 'mp4_h264', 'mp3', 'mjpeg_amr', 'mjpeg_wav', 'mjpeg_aac', 'mjpeg_aac_hf', 'audio_amr', 'audio_wav'];
$format = in_array($format, $validFormats) ? $format : '3gp';

if (isset($_GET['action']) && $_GET['action'] === 'sub' && isset($_GET['job'])) {
    $jobId = preg_replace('/[^a-f0-9]/', '', $_GET['job']);
    $jobDir = $baseTmpDir . '/ytdlp_job_' . $jobId;
    header('Content-Type: text/plain; charset=utf-8');
    $files = glob($jobDir . '/sub*.srt');
    if (!$files) {
        header('HTTP/1.0 404 Not Found');
        echo 'none ' . @file_get_contents($jobDir . '/sub_log.txt');
        exit;
    }
    readfile($files[0]);
    exit;
}

if (isset($_GET['action']) && $_GET['action'] === 'search' && !empty($_GET['query'])) {
    $q = $_GET['query'];
    $searchResults = ytdlpSearch($q);
    $pageQs = 'action=search&query=' . urlencode($q);
    if (empty($searchResults)) {
        $output = 'No results (yt-dlp search failed or returned nothing)';
    }

} elseif (isset($_GET['action']) && $_GET['action'] === 'subfeed' && !empty($_GET['channels'])) {
    $chList = explode(',', urldecode($_GET['channels']));
    $searchResults = [];
    foreach ($chList as $ch) {
        $ch = trim($ch);
        if (empty($ch)) continue;
        if (strpos($ch, 'http') !== 0) {
            if (strpos($ch, '/') === 0) {
                $ch = 'https://www.youtube.com' . $ch;
            } elseif (strpos($ch, '@') === 0) {
                $ch = 'https://www.youtube.com/' . $ch;
            } else {
                $ch = 'https://www.youtube.com/channel/' . $ch;
            }
        }

        $base = preg_replace('~/(videos|shorts|streams|live|featured|releases|playlists)$~', '', $ch);
        $tabs = [$base . '/videos', $base . '/releases', $base];
        $items = [];
        foreach ($tabs as $t) {
            list($items, ) = ytFlatList($t, '--playlist-end 1');
            if (!empty($items)) break;
        }
        if (!empty($items)) {
            $searchResults[] = $items[0];
        }
    }
    $listTitle = 'Subscriptions Feed:';
    $pageQs = 'action=subfeed&channels=' . urlencode($_GET['channels']);
    if (empty($searchResults)) {
        $output = 'No recent videos found from subscribed channels.';
    }

} elseif (isset($_GET['action']) && $_GET['action'] === 'channel' && !empty($_GET['channel'])) {
    $ch = rtrim($_GET['channel'], '/');
    if (preg_match('~^https?://(www\.)?youtube\.com/(channel/|@|c/|user/|playlist\?list=)~', $ch)) {
        if (strpos($ch, 'playlist?list=') !== false) {
            $tabs = [$ch];
            $listTitle = 'Playlist videos:';
        } else {
            $base = preg_replace('~/(videos|shorts|streams|live|featured|releases|playlists)$~', '', $ch);
            $tabs = [$base . '/videos', $base . '/releases', $base . '/playlists', $base];
            $listTitle = 'Channel content:';
        }
        $raw = '';
        foreach ($tabs as $t) {
            list($searchResults, $raw) = ytFlatList($t, '--playlist-end 50');
            if (!empty($searchResults)) break;
        }
        $pageQs = 'action=channel&channel=' . urlencode($_GET['channel']);
        if (empty($searchResults)) { $output = $raw; }
    } else {
        $output = 'Invalid channel URL';
    }
}
$page = isset($_GET['page']) ? max(1, (int)$_GET['page']) : 1;
$perPage = 10;
$totalResults = count($searchResults);
$totalPages = $totalResults > 0 ? ceil($totalResults / $perPage) : 1;
$pagedResults = array_slice($searchResults, ($page - 1) * $perPage, $perPage);

if (!empty($incomingUrl) && (isset($_POST['start_job']) || (isset($_GET['action']) && $_GET['action'] === 'run'))) {
    $urlInput = preg_replace('/([&?](list|index)=[^&]*)/', '', $incomingUrl);

    $jobId = bin2hex(random_bytes(6));
    $jobDir = $baseTmpDir . '/ytdlp_job_' . $jobId;
    mkdir($jobDir, 0700, true);

    file_put_contents($jobDir . '/status.json', json_encode([
    'state' => 'running',
    'step' => 'server cache the video...',
    'format' => $format,
    'url' => $urlInput
]));

startJobProcess($jobDir, $jobId, $format, $urlInput);

    header("Location: " . basename($_SERVER['PHP_SELF']) . "?job=" . $jobId);
    exit;
}

function fetchSubs($videoUrl, $jobDir) {
    if (!preg_match('~(?:v=|youtu\.be/|shorts/|embed/)([A-Za-z0-9_-]{11})~', $videoUrl, $m)) return 'bad video id';
    $post = json_encode(['context' => ['client' => ['clientName' => 'ANDROID', 'clientVersion' => '20.10.38', 'androidSdkVersion' => 30, 'hl' => 'en']], 'videoId' => $m[1]]);
    $ctx = stream_context_create(['http' => [
        'method' => 'POST', 'timeout' => 10, 'ignore_errors' => true,
        'header' => "Content-Type: application/json\r\nUser-Agent: com.google.android.youtube/20.10.38 (Linux; U; Android 11)\r\n",
        'content' => $post
    ]]);
    $r = @file_get_contents('https://www.youtube.com/youtubei/v1/player?prettyPrint=false', false, $ctx);
    $d = json_decode((string)$r, true);
    $tracks = $d['captions']['playerCaptionsTracklistRenderer']['captionTracks'] ?? [];
    if (!$tracks) return 'no caption tracks';
    $pick = $tracks[0];
    foreach ($tracks as $t) { if (strpos($t['languageCode'] ?? '', 'en') === 0) { $pick = $t; break; } }
    $ctx2 = stream_context_create(['http' => ['timeout' => 10, 'header' => "User-Agent: com.google.android.youtube/20.10.38 (Linux; U; Android 11)\r\n"]]);
    $j = @file_get_contents($pick['baseUrl'] . '&fmt=json3', false, $ctx2);
    $e = json_decode((string)$j, true);
    if (empty($e['events'])) return 'empty timedtext';
    $out = '';
    foreach ($e['events'] as $ev) {
        if (empty($ev['segs'])) continue;
        $txt = '';
        foreach ($ev['segs'] as $sg) $txt .= $sg['utf8'] ?? '';
        $txt = toLatin(str_replace("\n", ' ', $txt));
        if ($txt === '') continue;
        $s = (int)($ev['tStartMs'] ?? 0);
        $out .= $s . '|' . ($s + (int)($ev['dDurationMs'] ?? 2000)) . '|' . $txt . "\n";
    }
    if ($out === '') return 'no cues';
    file_put_contents($jobDir . '/sub.txt', $out);
    return '';
}

function renderJobStatusPanel($jobStatus, $currentJobId, $baseTmpDir) {
    $selfName = basename($_SERVER['PHP_SELF']);
    echo "<h3>Conversion Status:</h3>";
    echo '<div style="background: #fff3cd; padding: 10px; border: 1px solid #ffeeba; margin-bottom: 10px; border-radius: 3px;">';
    echo '<p><strong>Status:</strong> ' . htmlspecialchars($jobStatus['step'] ?? $jobStatus['state']) . '</p>';

    if ($jobStatus['state'] === 'running') {
        echo '<p>Processing in background... Please wait.</p>';
        echo '<a href="' . $selfName . '?action=refresh&job=' . $currentJobId . '"><button type="button">[RF] Refresh Status</button></a>';
        echo '<script>setTimeout(function(){ window.location.href = \'' . $selfName . '?action=refresh&job=' . $currentJobId . '\'; }, 4000);</script>';
    } elseif ($jobStatus['state'] === 'completed') {
        $dlFormat = $jobStatus['format'] ?? '3gp';
if ($dlFormat === 'audio_amr' || $dlFormat === 'audio_wav') {
    echo '<p style="color: green;"><strong>Ready to stream (audio only)!</strong></p>';
    echo '<p><audio controls autoplay src="' . $selfName . '?action=audio&job=' . $currentJobId . '"></audio></p>';
    echo '<p><a href="' . $selfName . '?action=audio&job=' . $currentJobId . '">Direct audio link</a></p>';
} elseif ($dlFormat === 'mjpeg_amr' || $dlFormat === 'mjpeg_wav' || $dlFormat === 'mjpeg_aac' || $dlFormat === 'mjpeg_aac_hf') {
            echo '<p style="color: green;"><strong>Ready to stream!</strong></p>';
            echo '<p><img src="' . $selfName . '?action=stream&job=' . $currentJobId . '" style="max-width:100%;border:1px solid #ccc;background:#000;" alt="MJPEG live stream"></p>';
            echo '<p><audio controls autoplay src="' . $selfName . '?action=audio&job=' . $currentJobId . '"></audio></p>';
            echo '<p style="font-size:11px;color:#777;">Video and audio are separate streams (no container muxing) and may drift out of sync; the video track is the raw yt-dlp download, only re-encoded to MJPEG on the fly as you view it.</p>';
        } else {
            $dlExt = ($dlFormat === 'mp3') ? 'mp3' : (($dlFormat === '3gp') ? '3gp' : 'mp4');
            echo '<p style="color: green;"><strong>Conversion complete!</strong></p>';
            echo '<a href="' . $selfName . '?download=' . $currentJobId . '&file=media.' . $dlExt . '"><button style="background: #28a745;">[DL] Download ' . strtoupper($dlFormat) . ' Video</button></a>';
        }
    } elseif ($jobStatus['state'] === 'error') {
        echo '<p style="color: red;"><strong>Error:</strong> ' . htmlspecialchars($jobStatus['msg'] ?? 'Unknown error') . '</p>';
        $logFile = $baseTmpDir . '/ytdlp_job_' . $currentJobId . '/log.txt';
        if (file_exists($logFile)) {
            echo "<pre>" . htmlspecialchars(file_get_contents($logFile)) . "</pre>";
        }
    }
    echo '</div>';
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Download YT</title>
    <style>
        * { box-sizing: border-box; }
        body { font-family: Tahoma, Arial, sans-serif; margin: 0; padding: 10px; background: #eef2f5; color: #111; font-size: 14px; }
        .container { width: 100%; max-width: 480px; margin: 0 auto; background: #fff; padding: 12px; border: 1px solid #ccd0d5; border-radius: 4px; }
        h2 { font-size: 16px; margin: 0 0 10px 0; color: #3b5998; border-bottom: 1px solid #eee; padding-bottom: 5px; }
        form { display: flex; gap: 8px; }
        input[type="text"] { flex: 1; width: 100%; padding: 8px; border: 1px solid #a4a4a4; border-radius: 3px; font-size: 14px; }
        button { padding: 8px 12px; background: #4267b2; color: white; border: none; border-radius: 3px; cursor: pointer; font-size: 14px; white-space: nowrap; }
        button:hover { background: #365899; }
        .video-item { padding: 8px 0; border-bottom: 1px dashed #ddd; display: flex; justify-content: space-between; align-items: center; }
        .video-item span { font-size: 13px; max-width: 70%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
        pre { background: #f1f1f1; padding: 10px; border: 1px solid #ccc; font-size: 12px; overflow-x: auto; }
    </style>
</head>
<body>
    <div class="container">
        <?php if ($jobStatus): renderJobStatusPanel($jobStatus, $currentJobId, $baseTmpDir); endif; ?>

        <h2><a href="<?php echo basename($_SERVER['PHP_SELF']); ?>" style="text-decoration: none; color: inherit;">Download YT</a></h2>

        <form method="GET" style="margin-bottom: 10px;">
            <input type="hidden" name="action" value="search">
            <input type="text" name="query" placeholder="Search videos..." value="<?php echo htmlspecialchars($_GET['query'] ?? ''); ?>" required>
            <button type="submit">Search</button>
        </form>

        <form method="GET" style="margin-bottom: 15px;">
            <input type="hidden" name="action" value="select">
            <input type="text" name="url" placeholder="Or paste video URL..." required>
            <button type="submit">Go</button>
        </form>

        <?php if (isset($_GET['action']) && $_GET['action'] === 'select' && !empty($_GET['url'])):
    $thumbId = '';
    if (preg_match('~(?:v=|youtu\.be/|shorts/|embed/)([A-Za-z0-9_-]{11})~', $_GET['url'], $tm)) {
        $thumbId = $tm[1];
    }
?>
    <h3>Select Conversion Format:</h3>
    <div style="background: #f8f9fa; padding: 12px; border: 1px solid #ccc; margin-bottom: 15px; border-radius: 3px;">
        <?php if ($thumbId): ?>
            <p style="margin: 0 0 8px 0; text-align: center;">
                <img src="https://i.ytimg.com/vi/<?php echo $thumbId; ?>/mqdefault.jpg"
                     alt="Thumbnail"
                     style="max-width:100%; height:auto; border:1px solid #ccc; background:#000;">
            </p>
        <?php endif; ?>
        <p style="word-break: break-all; font-size: 12px; color: #555;"><strong>URL:</strong> <?php echo htmlspecialchars($_GET['url']); ?></p>
        <div style="display: flex; flex-direction: column; gap: 6px;">
                    <a href="<?php echo basename($_SERVER['PHP_SELF']); ?>?action=run&url=<?php echo urlencode($_GET['url']); ?>&format=mp4_h264">MP4 (H264)</a><br>
                    <a href="<?php echo basename($_SERVER['PHP_SELF']); ?>?action=run&url=<?php echo urlencode($_GET['url']); ?>&format=mp4_mpeg4">MP4 176x144 (MPEG4)</a><br>
                    <a href="<?php echo basename($_SERVER['PHP_SELF']); ?>?action=run&url=<?php echo urlencode($_GET['url']); ?>&format=3gp">3GP 176x144 (H263)</a><br>
                    <a href="<?php echo basename($_SERVER['PHP_SELF']); ?>?action=run&url=<?php echo urlencode($_GET['url']); ?>&format=mp3">MP3 (Audio Only)</a><br>
                    <a href="<?php echo basename($_SERVER['PHP_SELF']); ?>?action=run&url=<?php echo urlencode($_GET['url']); ?>&format=mjpeg_amr">Stream MJPEG + AMR (low fps, live)</a><br>
                    <a href="<?php echo basename($_SERVER['PHP_SELF']); ?>?action=run&url=<?php echo urlencode($_GET['url']); ?>&format=mjpeg_aac">Stream MJPEG + AAC (low fps, live)</a><br>
                    <a href="<?php echo basename($_SERVER['PHP_SELF']); ?>?action=run&url=<?php echo urlencode($_GET['url']); ?>&format=mjpeg_aac_hf">Stream MJPEG + AAC HF (high fps, live)</a><br>
                    <a href="<?php echo basename($_SERVER['PHP_SELF']); ?>?action=run&url=<?php echo urlencode($_GET['url']); ?>&format=mjpeg_wav">Stream MJPEG + WAV (low fps, live)</a><br>
<a href="<?php echo basename($_SERVER['PHP_SELF']); ?>?action=run&url=<?php echo urlencode($_GET['url']); ?>&format=audio_amr">Stream Audio only (AMR, smallest)</a><br>
                </div>
            </div>
        <?php endif; ?>

        <?php if (!empty($pagedResults)): ?>
            <h3><?php echo htmlspecialchars($listTitle); ?></h3>
<?php foreach ($pagedResults as $video): ?>
<div class="video-item">
    <?php if (in_array($video['kind'], ['channel', 'playlist'])):
        $chUrl = $video['url'] ?: ('https://www.youtube.com/channel/' . $video['id']); ?>
                <?php if ($video['kind'] === 'channel'): ?>
            <img src="<?php echo basename($_SERVER['PHP_SELF']); ?>?action=chthumb&u=<?php echo urlencode($chUrl); ?>"
                 width="36" height="36" loading="lazy" alt=""
                 style="border-radius:50%;margin-right:8px;background:#ccc;object-fit:cover;flex:none;">
        <?php endif; ?>
        <span>[<?php echo ucfirst($video['kind']); ?>] <?php echo htmlspecialchars($video['title']); ?></span>
        <a href="<?php echo basename($_SERVER['PHP_SELF']); ?>?action=channel&channel=<?php echo urlencode($chUrl); ?>"
           style="padding: 3px 8px; font-size: 12px; background: #28a745; color: white; text-decoration: none; border-radius: 3px;">Open</a>
    <?php else: ?>
        <span><?php echo htmlspecialchars($video['title']); ?></span>
        <a href="<?php echo basename($_SERVER['PHP_SELF']); ?>?action=select&url=<?php echo urlencode('https://www.youtube.com/watch?v=' . $video['id']); ?>"
           style="padding: 3px 8px; font-size: 12px; background: #4267b2; color: white; text-decoration: none; border-radius: 3px;">Select</a>
    <?php endif; ?>
</div>
<?php endforeach; ?>
            <?php if ($totalPages > 1): ?>
    <div style="margin-top: 10px; display: flex; justify-content: space-between; align-items: center; font-size: 13px;">
        <?php if ($page > 1): ?>
            <a href="<?php echo basename($_SERVER['PHP_SELF']); ?>?<?php echo $pageQs; ?>&page=<?php echo $page - 1; ?>">« Prev</a>
        <?php else: ?>
            <span></span>
        <?php endif; ?>
        <span>Page <?php echo $page; ?> of <?php echo $totalPages; ?></span>
        <?php if ($page < $totalPages): ?>
            <a href="<?php echo basename($_SERVER['PHP_SELF']); ?>?<?php echo $pageQs; ?>&page=<?php echo $page + 1; ?>">Next »</a>
        <?php else: ?>
            <span></span>
        <?php endif; ?>
    </div>
<?php endif; ?>
        <?php endif; ?>

        <?php if (!empty($output)): ?>
            <h3>Output:</h3>
            <pre><?php echo htmlspecialchars($output); ?></pre>
        <?php endif; ?>
    </div>
</body>
</html>
