$ErrorActionPreference = 'Continue'

# Move Directories
$dirs = @(
    @{ Old="d:\generator\app\src\main\java\com\example\generator"; New="d:\generator\app\src\main\java\com\myPage\pdfGenerator"; Base="d:\generator\app\src\main\java\com\myPage" },
    @{ Old="d:\generator\app\src\test\java\com\example\generator"; New="d:\generator\app\src\test\java\com\myPage\pdfGenerator"; Base="d:\generator\app\src\test\java\com\myPage" },
    @{ Old="d:\generator\app\src\androidTest\java\com\example\generator"; New="d:\generator\app\src\androidTest\java\com\myPage\pdfGenerator"; Base="d:\generator\app\src\androidTest\java\com\myPage" }
)

foreach ($d in $dirs) {
    if (Test-Path $d.Old) {
        New-Item -ItemType Directory -Force -Path $d.Base | Out-Null
        Move-Item -Path $d.Old -Destination $d.New
    }
}

# Delete com/example if it exists and is now empty
if (Test-Path "d:\generator\app\src\main\java\com\example") {
    Remove-Item "d:\generator\app\src\main\java\com\example" -Recurse -Force -ErrorAction SilentlyContinue
}

# Replace text in files
$files = Get-ChildItem -Path d:\generator\app -Recurse -Include *.kt, *.xml, *.kts, *.pro
foreach ($f in $files) {
    if (Test-Path -PathType Leaf $f.FullName) {
        $txt = [System.IO.File]::ReadAllText($f.FullName)
        if ($txt -match 'com\.example\.generator') {
            $txt = $txt -replace 'com\.example\.generator', 'com.myPage.pdfGenerator'
            [System.IO.File]::WriteAllText($f.FullName, $txt)
        }
    }
}
Write-Output "Package renaming completed successfully."
