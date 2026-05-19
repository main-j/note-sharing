param(
    [switch]$SkipMySQL,
    [switch]$SkipMongoDB,
    [switch]$SkipRedis,
    [switch]$SkipRabbitMQ,
    [switch]$SkipElasticsearch,
    [switch]$SkipMinIO,
    # Optional: start Apache Flink standalone cluster (JobManager + TaskManager). Not required if jobs use createLocalEnvironment().
    [switch]$StartFlinkCluster
)

. "$PSScriptRoot\config.ps1"

New-Item -ItemType Directory -Force -Path $BackendDeps.LogsDir | Out-Null

Write-Host "Starting backend dependencies..."

if (-not $SkipMySQL) {
    Start-ServiceIfPresent -Label "MySQL" -Candidates $BackendDeps.MySQLServiceCandidates
}

if (-not $SkipMongoDB) {
    Start-ServiceIfPresent -Label "MongoDB" -Candidates $BackendDeps.MongoServiceCandidates
}

if (-not $SkipRabbitMQ) {
    Start-ServiceIfPresent -Label "RabbitMQ" -Candidates $BackendDeps.RabbitMQServiceCandidates
}

if (-not $SkipRedis -and $BackendDeps.UseWslRedis) {
    Write-Host "Starting Redis in WSL distribution: $($BackendDeps.WslDistribution)"
    wsl -d $BackendDeps.WslDistribution -- bash -lc "sudo service redis-server start"
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "Redis start command failed. Open Ubuntu and run: sudo service redis-server start"
    }
}

if (-not $SkipElasticsearch) {
    if (Test-PortOpen -Port $BackendDeps.Ports.Elasticsearch) {
        Write-Host "Elasticsearch already listening on port $($BackendDeps.Ports.Elasticsearch)"
    } elseif (Test-Path $BackendDeps.ElasticsearchBat) {
        Write-Host "Starting Elasticsearch: $($BackendDeps.ElasticsearchBat)"
        Start-Process -FilePath "cmd.exe" `
            -ArgumentList "/k `"$($BackendDeps.ElasticsearchBat)`"" `
            -WorkingDirectory (Split-Path $BackendDeps.ElasticsearchBat -Parent)
    } else {
        Write-Warning "Elasticsearch script not found: $($BackendDeps.ElasticsearchBat)"
    }
}

if (-not $SkipMinIO) {
    if (Test-PortOpen -Port $BackendDeps.Ports.MinIO) {
        Write-Host "MinIO already listening on port $($BackendDeps.Ports.MinIO)"
    } elseif (Test-Path $BackendDeps.MinioExe) {
        New-Item -ItemType Directory -Force -Path $BackendDeps.MinioDataDir | Out-Null
        Write-Host "Starting MinIO: $($BackendDeps.MinioExe)"
        $minioCommand = @"
`$env:MINIO_ROOT_USER = '$($BackendDeps.MinioRootUser)'
`$env:MINIO_ROOT_PASSWORD = '$($BackendDeps.MinioRootPassword)'
& '$($BackendDeps.MinioExe)' server '$($BackendDeps.MinioDataDir)' --console-address ':$($BackendDeps.Ports.MinIOConsole)'
"@
        Start-Process -FilePath "powershell.exe" `
            -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $minioCommand `
            -WorkingDirectory (Split-Path $BackendDeps.MinioExe -Parent)
    } else {
        Write-Warning "MinIO executable not found: $($BackendDeps.MinioExe)"
    }
}

if ($StartFlinkCluster) {
    if (Test-PortOpen -Port $BackendDeps.Ports.FlinkWebUI) {
        Write-Host "Flink Web UI port $($BackendDeps.Ports.FlinkWebUI) already open (cluster may already be running)."
    } elseif (Test-WslPortOpen -Port $BackendDeps.Ports.FlinkWebUI) {
        Write-Host "Flink Web UI port $($BackendDeps.Ports.FlinkWebUI) is already open inside WSL (cluster may already be running)."
    } else {
        $startCluster = $null
        foreach ($candidate in @(
            Join-Path $BackendDeps.FlinkHome "bin\start-cluster.bat"
            Join-Path $BackendDeps.FlinkHome "bin\start-cluster.cmd"
        )) {
            if (Test-Path $candidate) {
                $startCluster = $candidate
                break
            }
        }

        if ($null -ne $startCluster) {
            Write-Host "Starting Flink cluster: $startCluster"
            Start-Process -FilePath "cmd.exe" `
                -ArgumentList "/k `"$startCluster`"" `
                -WorkingDirectory (Split-Path $startCluster -Parent)
        } elseif (Test-Path (Join-Path $BackendDeps.FlinkHome "bin\start-cluster.sh")) {
            $wslFlinkHome = ConvertTo-WslPath -WindowsPath $BackendDeps.FlinkHome
            $javaExport = ""
            if ($BackendDeps.FlinkWslJavaHome -and $BackendDeps.FlinkWslJavaHome.Trim()) {
                $javaExport = "export JAVA_HOME='$($BackendDeps.FlinkWslJavaHome)'; "
            }
            $bashCmd = "${javaExport}cd '$wslFlinkHome' && ./bin/start-cluster.sh; echo; echo Flink started. Keep this WSL window open while using the cluster.; exec bash"
            Write-Host "Starting Flink cluster in a persistent WSL window $($BackendDeps.WslDistribution): $wslFlinkHome"
            Start-Process -FilePath "powershell.exe" `
                -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-Command", "wsl -d $($BackendDeps.WslDistribution) -- bash -lc `"$bashCmd`""
            Start-Sleep -Seconds 5
            if (-not (Test-WslPortOpen -Port $BackendDeps.Ports.FlinkWebUI)) {
                Write-Warning "Flink Web UI did not open inside WSL. In Ubuntu: sudo apt install -y openjdk-17-jdk && java -version"
            }
        } else {
            Write-Warning "Flink start-cluster script not found under $($BackendDeps.FlinkHome)\bin. Extract Flink 1.19.x there or set FlinkHome in config.ps1."
        }
    }
}

Write-Host ""
Write-Host "Waiting a few seconds before port checks..."
Start-Sleep -Seconds 5
Show-DependencyPorts

Write-Host ""
Write-Host "Keep the Elasticsearch and MinIO windows open while developing."
if ($StartFlinkCluster) {
    Write-Host "Flink cluster was started in a separate window. Web UI: http://127.0.0.1:$($BackendDeps.Ports.FlinkWebUI)/"
}
