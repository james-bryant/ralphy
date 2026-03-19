param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$MavenArgs
)

$ErrorActionPreference = "Stop"

function Remove-WithRetry {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [int]$Attempts = 10,
        [int]$DelayMilliseconds = 500
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }

    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        try {
            Remove-Item -LiteralPath $Path -Recurse -Force -ErrorAction Stop
            return
        } catch {
            if ($attempt -eq $Attempts) {
                throw
            }

            Start-Sleep -Milliseconds $DelayMilliseconds
        }
    }
}

Remove-WithRetry -Path "$PSScriptRoot\..\target\dist\windows"
Remove-WithRetry -Path "$PSScriptRoot\..\target\jpackage\windows"

& "$PSScriptRoot\..\mvnw.cmd" clean verify "-Ppackage-native,package-windows" @MavenArgs
