param(
    [string]$MarkersFile = ".protected-source-markers",
    [string]$GitRef = "HEAD",
    [string]$JarPath,
    [switch]$ScanWorkingTree,
    [string[]]$TextContent
)

$ErrorActionPreference = "Stop"
$script:FailureCategory = "verification"
$script:CurrentStage = "startup"
$script:Findings = [System.Collections.Generic.List[object]]::new()

function Invoke-CheckedCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Command,
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,
        [Parameter(Mandatory = $true)]
        [string]$StandardOutputPath,
        [Parameter(Mandatory = $true)]
        [string]$StandardErrorPath
    )

    & $Command @Arguments 1> $StandardOutputPath 2> $StandardErrorPath
    if ($LASTEXITCODE -ne 0) {
        $script:FailureCategory = "external-command"
        throw "External command failed."
    }
}

function Test-RipgrepMatch {
    param(
        [Parameter(Mandatory = $true)]
        [string]$MarkerPatternFile,
        [Parameter(Mandatory = $true)]
        [string]$SearchPath,
        [Parameter(Mandatory = $true)]
        [bool]$IgnoreRepositoryRules,
        [Parameter(Mandatory = $true)]
        [string]$StandardErrorPath,
        [string[]]$AdditionalArguments = @()
    )

    $arguments = [System.Collections.Generic.List[string]]::new()
    @("--text", "--hidden") | ForEach-Object { $arguments.Add($_) }
    if ($IgnoreRepositoryRules) {
        $arguments.Add("--no-ignore")
    }
    @("--fixed-strings", "--ignore-case", "--quiet", "-f", $MarkerPatternFile) |
        ForEach-Object { $arguments.Add($_) }
    $AdditionalArguments | ForEach-Object { $arguments.Add($_) }
    $arguments.Add("--")
    $arguments.Add($SearchPath)

    & rg @arguments 1> $null 2> $StandardErrorPath
    $ripgrepExitCode = $LASTEXITCODE
    if ($ripgrepExitCode -eq 0) {
        return $true
    }
    if ($ripgrepExitCode -eq 1) {
        return $false
    }

    $script:FailureCategory = "ripgrep"
    throw "Ripgrep failed."
}

function Test-PathScope {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Scope,
        [Parameter(Mandatory = $true)]
        [string]$SearchPath,
        [Parameter(Mandatory = $true)]
        [bool]$IgnoreRepositoryRules,
        [Parameter(Mandatory = $true)]
        [string[]]$Markers,
        [Parameter(Mandatory = $true)]
        [string]$PatternFile,
        [Parameter(Mandatory = $true)]
        [string]$StandardErrorPath,
        [string[]]$AdditionalArguments = @()
    )

    for ($index = 0; $index -lt $Markers.Count; $index++) {
        [System.IO.File]::WriteAllText(
            $PatternFile,
            $Markers[$index],
            [System.Text.UTF8Encoding]::new($false)
        )
        if (Test-RipgrepMatch `
                -MarkerPatternFile $PatternFile `
                -SearchPath $SearchPath `
                -IgnoreRepositoryRules $IgnoreRepositoryRules `
                -StandardErrorPath $StandardErrorPath `
                -AdditionalArguments $AdditionalArguments) {
            $script:Findings.Add([pscustomobject]@{
                Scope = $Scope
                MarkerIndex = $index + 1
            })
        }
    }
}

function Test-TextScope {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Scope,
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string[]]$Content,
        [Parameter(Mandatory = $true)]
        [string[]]$Markers
    )

    for ($index = 0; $index -lt $Markers.Count; $index++) {
        foreach ($item in $Content) {
            if ($null -ne $item -and
                $item.IndexOf($Markers[$index], [System.StringComparison]::OrdinalIgnoreCase) -ge 0) {
                $script:Findings.Add([pscustomobject]@{
                    Scope = $Scope
                    MarkerIndex = $index + 1
                })
                break
            }
        }
    }
}

function Get-ArchiveEntries {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ArchivePath,
        [Parameter(Mandatory = $true)]
        [string]$OutputPath,
        [Parameter(Mandatory = $true)]
        [string]$StandardErrorPath
    )

    Invoke-CheckedCommand `
        -Command "tar" `
        -Arguments @("-tf", $ArchivePath) `
        -StandardOutputPath $OutputPath `
        -StandardErrorPath $StandardErrorPath
}

function Expand-TarArchive {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ArchivePath,
        [Parameter(Mandatory = $true)]
        [string]$DestinationPath,
        [Parameter(Mandatory = $true)]
        [string]$StandardOutputPath,
        [Parameter(Mandatory = $true)]
        [string]$StandardErrorPath
    )

    New-Item -ItemType Directory -Path $DestinationPath -Force | Out-Null
    Invoke-CheckedCommand `
        -Command "tar" `
        -Arguments @("-xf", $ArchivePath, "-C", $DestinationPath) `
        -StandardOutputPath $StandardOutputPath `
        -StandardErrorPath $StandardErrorPath
}

function Assert-ChildPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ParentPath,
        [Parameter(Mandatory = $true)]
        [string]$ChildPath
    )

    $parentFullPath = [System.IO.Path]::GetFullPath($ParentPath).TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar
    ) + [System.IO.Path]::DirectorySeparatorChar
    $childFullPath = [System.IO.Path]::GetFullPath($ChildPath)
    if (-not $childFullPath.StartsWith($parentFullPath, [System.StringComparison]::OrdinalIgnoreCase)) {
        $script:FailureCategory = "cleanup-path"
        throw "Temporary path is outside the verification directory."
    }
}

$exitCode = 0
$temporaryRoot = $null
$verificationRoot = $null

try {
    $script:CurrentStage = "input-validation"
    if ([string]::IsNullOrWhiteSpace($GitRef) -or $GitRef.StartsWith("-")) {
        $script:FailureCategory = "git-ref-validation"
        throw "Git reference is invalid."
    }

    if (-not (Test-Path -LiteralPath $MarkersFile -PathType Leaf)) {
        $script:FailureCategory = "marker-validation"
        throw "Marker file is invalid."
    }
    $resolvedMarkersFile = (Resolve-Path -LiteralPath $MarkersFile).Path
    $markers = [System.IO.File]::ReadAllLines($resolvedMarkersFile, [System.Text.Encoding]::UTF8)
    if ($markers.Count -eq 0) {
        $script:FailureCategory = "marker-validation"
        throw "Marker file is invalid."
    }

    $uniqueMarkers = [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::OrdinalIgnoreCase
    )
    $normalizedMarkers = [System.Collections.Generic.List[string]]::new()
    foreach ($marker in $markers) {
        if ([string]::IsNullOrWhiteSpace($marker)) {
            $script:FailureCategory = "marker-validation"
            throw "Marker file is invalid."
        }
        if ($uniqueMarkers.Add($marker)) {
            $normalizedMarkers.Add($marker)
        }
    }
    $markers = $normalizedMarkers.ToArray()

    $script:CurrentStage = "repository-root-command"
    $repositoryRootOutput = & git rev-parse --show-toplevel 2> $null
    if ($LASTEXITCODE -ne 0) {
        $script:FailureCategory = "external-command"
        throw "External command failed."
    }
    $script:CurrentStage = "repository-root-read"
    $repositoryRoot = ($repositoryRootOutput -join [System.Environment]::NewLine).Trim()

    if ([string]::IsNullOrWhiteSpace($repositoryRoot)) {
        $script:FailureCategory = "repository"
        throw "Repository root is unavailable."
    }

    $script:CurrentStage = "verification-root-path"
    $verificationRoot = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot "target/verification"))
    $temporaryRoot = Join-Path $verificationRoot ([System.Guid]::NewGuid().ToString("N"))
    Assert-ChildPath -ParentPath $verificationRoot -ChildPath $temporaryRoot
    $script:CurrentStage = "verification-root-create"
    New-Item -ItemType Directory -Path $temporaryRoot -Force | Out-Null

    $commandOutput = Join-Path $temporaryRoot "command-output.txt"
    $commandError = Join-Path $temporaryRoot "command-error.txt"
    $patternFile = Join-Path $temporaryRoot "marker-pattern.txt"

    $script:CurrentStage = "git-tree-archive"
    $gitArchive = Join-Path $temporaryRoot "git-tree.tar"
    Invoke-CheckedCommand `
        -Command "git" `
        -Arguments @("archive", "--format=tar", "--output=$gitArchive", $GitRef) `
        -StandardOutputPath $commandOutput `
        -StandardErrorPath $commandError

    $script:CurrentStage = "git-tree-path"
    $gitEntryPaths = Join-Path $temporaryRoot "git-entry-paths.txt"
    Get-ArchiveEntries `
        -ArchivePath $gitArchive `
        -OutputPath $gitEntryPaths `
        -StandardErrorPath $commandError
    Test-PathScope `
        -Scope "git-tree-path" `
        -SearchPath $gitEntryPaths `
        -IgnoreRepositoryRules $true `
        -Markers $markers `
        -PatternFile $patternFile `
        -StandardErrorPath $commandError

    $script:CurrentStage = "git-tree-content"
    $gitTree = Join-Path $temporaryRoot "git-tree"
    Expand-TarArchive `
        -ArchivePath $gitArchive `
        -DestinationPath $gitTree `
        -StandardOutputPath $commandOutput `
        -StandardErrorPath $commandError
    Test-PathScope `
        -Scope "git-tree-content" `
        -SearchPath $gitTree `
        -IgnoreRepositoryRules $true `
        -Markers $markers `
        -PatternFile $patternFile `
        -StandardErrorPath $commandError

    $script:CurrentStage = "git-history"
    $gitHistory = Join-Path $temporaryRoot "git-history.txt"
    Invoke-CheckedCommand `
        -Command "git" `
        -Arguments @("log", $GitRef, "--format=%H%n%s%n%b") `
        -StandardOutputPath $gitHistory `
        -StandardErrorPath $commandError
    Test-PathScope `
        -Scope "git-history" `
        -SearchPath $gitHistory `
        -IgnoreRepositoryRules $true `
        -Markers $markers `
        -PatternFile $patternFile `
        -StandardErrorPath $commandError

    if ($ScanWorkingTree) {
        $script:CurrentStage = "working-tree-content"
        Test-PathScope `
            -Scope "working-tree-content" `
            -SearchPath $repositoryRoot `
            -IgnoreRepositoryRules $false `
            -Markers $markers `
            -PatternFile $patternFile `
            -StandardErrorPath $commandError `
            -AdditionalArguments @("--glob=!.git/**")

        $script:CurrentStage = "working-tree-path"
        $workingTreePaths = Join-Path $temporaryRoot "working-tree-paths.txt"
        Invoke-CheckedCommand `
            -Command "git" `
            -Arguments @("ls-files", "--cached", "--others", "--exclude-standard") `
            -StandardOutputPath $workingTreePaths `
            -StandardErrorPath $commandError
        Test-PathScope `
            -Scope "working-tree-path" `
            -SearchPath $workingTreePaths `
            -IgnoreRepositoryRules $true `
            -Markers $markers `
            -PatternFile $patternFile `
            -StandardErrorPath $commandError
    }

    if (-not [string]::IsNullOrWhiteSpace($JarPath)) {
        $script:CurrentStage = "jar"
        if (-not (Test-Path -LiteralPath $JarPath -PathType Leaf)) {
            $script:FailureCategory = "jar-validation"
            throw "JAR path is invalid."
        }

        $jarRoot = Join-Path $temporaryRoot "jar"
        New-Item -ItemType Directory -Path $jarRoot -Force | Out-Null
        $jarZip = Join-Path $jarRoot "artifact.zip"
        Copy-Item -LiteralPath $JarPath -Destination $jarZip

        $jarEntries = Join-Path $jarRoot "entry-paths.txt"
        $jarEntryNames = [System.IO.Compression.ZipFile]::OpenRead($jarZip)
        try {
            [System.IO.File]::WriteAllLines(
                $jarEntries,
                [string[]]($jarEntryNames.Entries | ForEach-Object { $_.FullName }),
                [System.Text.UTF8Encoding]::new($false)
            )
        }
        finally {
            $jarEntryNames.Dispose()
        }
        Test-PathScope `
            -Scope "jar-path" `
            -SearchPath $jarEntries `
            -IgnoreRepositoryRules $true `
            -Markers $markers `
            -PatternFile $patternFile `
            -StandardErrorPath $commandError

        $jarContent = Join-Path $jarRoot "content"
        Expand-Archive -LiteralPath $jarZip -DestinationPath $jarContent -Force
        Test-PathScope `
            -Scope "jar-content" `
            -SearchPath $jarContent `
            -IgnoreRepositoryRules $true `
            -Markers $markers `
            -PatternFile $patternFile `
            -StandardErrorPath $commandError
    }

    if ($null -ne $TextContent -and $TextContent.Count -gt 0) {
        $script:CurrentStage = "text-content"
        Test-TextScope -Scope "text-content" -Content $TextContent -Markers $markers
    }

    if ($script:Findings.Count -gt 0) {
        $exitCode = 1
    }
}
catch {
    if ($script:FailureCategory -eq "verification") {
        $script:FailureCategory = "unexpected-$($script:CurrentStage)"
    }
    $exitCode = 2
}
finally {
    if ($null -ne $temporaryRoot -and (Test-Path -LiteralPath $temporaryRoot)) {
        try {
            Assert-ChildPath -ParentPath $verificationRoot -ChildPath $temporaryRoot
            Remove-Item -LiteralPath $temporaryRoot -Recurse -Force
        }
        catch {
            $script:FailureCategory = "cleanup"
            $exitCode = 2
        }
    }
}

if ($exitCode -eq 1) {
    foreach ($finding in $script:Findings) {
        Write-Error ("Protected marker detected; scope={0}; marker-index={1}." -f `
            $finding.Scope, $finding.MarkerIndex) -ErrorAction Continue
    }
}
elseif ($exitCode -eq 2) {
    Write-Error ("Protected-source verification failed; category={0}." -f $script:FailureCategory) `
        -ErrorAction Continue
}
else {
    Write-Output "Protected-source verification passed."
}

exit $exitCode
