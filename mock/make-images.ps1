Add-Type -AssemblyName System.Drawing
$root = 'C:\Users\15572\Documents\shell\ech0-mobile\mock\files'
New-Item -ItemType Directory -Force -Path $root | Out-Null
$shot = [System.Drawing.Image]::FromFile('C:\Users\15572\Documents\shell\ech0-mobile\docs\screenshots\01_timeline.png')

function Save-Crop($src, $x, $y, $w, $h, $out) {
    $bmp = New-Object System.Drawing.Bitmap -ArgumentList 1200, 900
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $dest = New-Object System.Drawing.Rectangle -ArgumentList 0, 0, 1200, 900
    $srcRect = New-Object System.Drawing.Rectangle -ArgumentList $x, $y, $w, $h
    $g.DrawImage($src, $dest, $srcRect, [System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose()
    $bmp.Save((Join-Path $root $out), [System.Drawing.Imaging.ImageFormat]::Jpeg)
    $bmp.Dispose()
}

Save-Crop $shot 100 1712 420 315 'street.jpg'
Save-Crop $shot 560 1712 420 315 'mountain.jpg'
$shot.Dispose()
Copy-Item (Join-Path $root 'mountain.jpg') (Join-Path $root 'clouds.jpg') -Force
Get-ChildItem $root | Select-Object Name, Length | Format-Table -AutoSize

