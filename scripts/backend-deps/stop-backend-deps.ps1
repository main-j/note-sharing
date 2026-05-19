param(
    [switch]$SkipMySQL,
    [switch]$SkipMongoDB,
    [switch]$SkipRedis,
    [switch]$SkipRabbitMQ,
    [switch]$SkipElasticsearch,
    [switch]$SkipMinIO,
    [switch]$StopFlinkCluster
)

. "$PSScriptRoot\config.ps1"

Write-Host "Stopping backend dependencies..."

if ($StopFlinkCluster) {
    $stopCluster = $null
    foreach ($candidate in @(
        Join-Path $BackendDeps.FlinkHome "bin\stop-cluster.bat"
        Join-Path $BackendDeps.FlinkHome "bin\stop-cluster.cmd"
    )) {
        if (Test-Path $candidate) {
            $stopCluster = $candidate
            break
        }
    }

    if ($null -ne $stopCluster) {
        Write-Host "Stopping Flink cluster via: $stopCluster"
        Push-Location (Split-Path $stopCluster -Parent)
        try {
            & cmd.exe /c "`"$stopCluster`""
        } finally {
            Pop-Location
        }
    } elseif (Test-Path (Join-Path $BackendDeps.FlinkHome "bin\stop-cluster.sh")) {
        $wslFlinkHome = ConvertTo-WslPath -WindowsPath $BackendDeps.FlinkHome
        $javaExport = ""
        if ($BackendDeps.FlinkWslJavaHome -and $BackendDeps.FlinkWslJavaHome.Trim()) {
            $javaExport = "export JAVA_HOME='$($BackendDeps.FlinkWslJavaHome)'; "
        }
        $bashCmd = "${javaExport}cd '$wslFlinkHome' && ./bin/stop-cluster.sh"
        Write-Host "Stopping Flink cluster via WSL $($BackendDeps.WslDistribution): $wslFlinkHome"
        wsl -d $BackendDeps.WslDistribution -- bash -lc "$bashCmd"
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "Flink stop-cluster.sh returned non-zero exit code."
        }
        Start-Sleep -Seconds 2
        if (Test-WslPortOpen -Port $BackendDeps.Ports.FlinkWebUI) {
            Write-Host "Cleaning up residual Flink processes in WSL..."
            wsl -d $BackendDeps.WslDistribution -- bash -lc "pkill -f StandaloneSessionClusterEntrypoint || true; pkill -f TaskManagerRunner || true"
        }
    } else {
        Write-Warning "Flink stop-cluster script not found under $($BackendDeps.FlinkHome)\bin."
    }
}

if (-not $SkipMinIO) {
    $minioProcesses = Get-CimInstance Win32_Process |
        Where-Object { $_.Name -ieq "minio.exe" -or ($_.CommandLine -like "*$($BackendDeps.MinioExe)*") -or ($_.CommandLine -like "*$($BackendDeps.MinioDataDir)*") }

    if ($minioProcesses) {
        foreach ($process in $minioProcesses) {
            Write-Host "Stopping MinIO process: $($process.ProcessId)"
            Stop-Process -Id $process.ProcessId -Force
        }
    } else {
        Write-Host "MinIO process not found."
    }
}

if (-not $SkipElasticsearch) {
    $escapedHome = $BackendDeps.ElasticsearchHome.Replace("\", "\\")
    $esProcesses = Get-CimInstance Win32_Process |
        Where-Object { $_.CommandLine -match [regex]::Escape($BackendDeps.ElasticsearchHome) -or $_.CommandLine -match [regex]::Escape($escapedHome) }

    if ($esProcesses) {
        foreach ($process in $esProcesses) {
            Write-Host "Stopping Elasticsearch process: $($process.ProcessId)"
            Stop-Process -Id $process.ProcessId -Force
        }
    } else {
        Write-Host "Elasticsearch process not found."
    }
}

if (-not $SkipRedis -and $BackendDeps.UseWslRedis) {
    Write-Host "Stopping Redis in WSL distribution: $($BackendDeps.WslDistribution)"
    wsl -d $BackendDeps.WslDistribution -- bash -lc "sudo service redis-server stop"
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "Redis stop command failed. Open Ubuntu and run: sudo service redis-server stop"
    }
}

if (-not $SkipRabbitMQ) {
    Stop-ServiceIfPresent -Label "RabbitMQ" -Candidates $BackendDeps.RabbitMQServiceCandidates
}

if (-not $SkipMongoDB) {
    Stop-ServiceIfPresent -Label "MongoDB" -Candidates $BackendDeps.MongoServiceCandidates
}

if (-not $SkipMySQL) {
    Stop-ServiceIfPresent -Label "MySQL" -Candidates $BackendDeps.MySQLServiceCandidates
}

Start-Sleep -Seconds 3
Show-DependencyPorts
