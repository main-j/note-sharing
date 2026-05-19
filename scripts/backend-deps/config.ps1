# Shared configuration for local backend dependency scripts.
# Adjust paths here if you move a dependency again.

$Script:BackendDeps = @{
    Ports = @{
        MySQL                 = 3306
        MongoDB               = 27017
        Redis                 = 6379
        RabbitMQ              = 5672
        RabbitMQManagement    = 15672
        Elasticsearch         = 9200
        MinIO                 = 9000
        MinIOConsole          = 9001
        # Standalone Flink cluster Web UI (JobManager REST). Only used if you start bin\start-cluster.*
        FlinkWebUI            = 8081
    }

    # Windows services are discovered from these candidate names.
    MySQLServiceCandidates    = @("MySQL80", "MySQL84", "MySQL")
    MongoServiceCandidates    = @("MongoDB", "MongoDB Server")
    RabbitMQServiceCandidates = @("RabbitMQ")
    RabbitMQSbin              = "C:\Program Files\RabbitMQ Server\rabbitmq_server-4.3.0\sbin"

    # Redis is currently expected to run inside WSL Ubuntu.
    UseWslRedis               = $true
    WslDistribution           = "Ubuntu-24.04"
    RedisDatabase             = 0

    ElasticsearchHome         = "C:\programming\elasticsearch-9.4.1"
    ElasticsearchBat          = "C:\programming\elasticsearch-9.4.1\bin\elasticsearch.bat"

    MinioExe                  = "C:\programming\minio\minio.exe"
    MinioDataDir              = "C:\programming\minio\data"
    MinioRootUser             = "name"
    MinioRootPassword         = "password"
    MinioBucket               = "notesharing"

    # Apache Flink (optional standalone cluster). Version should match Login_api build.gradle flinkVersion (1.19.3).
    # Download: https://flink.apache.org/downloads.html — extract to this path.
    # Official .tgz has NO start-cluster.bat on Windows (use WSL + bin/start-cluster.sh). See README.
    # Note: BehaviorSearchJob currently uses createLocalEnvironment(), so a cluster is NOT required unless you change the code to submit remotely.
    FlinkHome                 = "C:\programming\flink-1.19.3"
    # JDK inside WSL for Flink (install: sudo apt install -y openjdk-17-jdk). Leave empty to use default java on PATH.
    FlinkWslJavaHome          = "/usr/lib/jvm/java-17-openjdk-amd64"

    LogsDir                   = Join-Path $PSScriptRoot "logs"
}

function ConvertTo-WslPath {
    param(
        [Parameter(Mandatory = $true)][string]$WindowsPath
    )

    $trimmed = $WindowsPath.TrimEnd('\', ' ')
    if ($trimmed -match '^([A-Za-z]):\\(.*)$') {
        $drive = $Matches[1].ToLowerInvariant()
        $rest = ($Matches[2] -replace '\\', '/')
        return "/mnt/$drive/$rest"
    }

    return $WindowsPath
}

function Test-PortOpen {
    param(
        [Parameter(Mandatory = $true)][int]$Port,
        [string]$HostName = "127.0.0.1"
    )

    try {
        return (Test-NetConnection -ComputerName $HostName -Port $Port -WarningAction SilentlyContinue).TcpTestSucceeded
    } catch {
        return $false
    }
}

function Test-WslPortOpen {
    param(
        [Parameter(Mandatory = $true)][int]$Port,
        [string]$Distribution = $Script:BackendDeps.WslDistribution
    )

    try {
        $bashCmd = "ss -ltnH 'sport = :$Port' | wc -l"
        $output = wsl -d $Distribution -- bash -lc "$bashCmd" 2>$null
        $countLine = $output | Where-Object { $_ -match '^\s*\d+\s*$' } | Select-Object -Last 1
        return ($null -ne $countLine -and [int]$countLine.Trim() -gt 0)
    } catch {
        return $false
    }
}

function Get-FirstExistingService {
    param(
        [Parameter(Mandatory = $true)][string[]]$Candidates
    )

    foreach ($candidate in $Candidates) {
        $service = Get-Service -Name $candidate -ErrorAction SilentlyContinue
        if ($null -ne $service) {
            return $service
        }
    }

    return $null
}

function Start-ServiceIfPresent {
    param(
        [Parameter(Mandatory = $true)][string]$Label,
        [Parameter(Mandatory = $true)][string[]]$Candidates
    )

    $service = Get-FirstExistingService -Candidates $Candidates
    if ($null -eq $service) {
        Write-Warning "$Label service not found. Checked: $($Candidates -join ', ')"
        return
    }

    if ($service.Status -ne "Running") {
        Write-Host "Starting $Label service: $($service.Name)"
        Start-Service -Name $service.Name
        $service.WaitForStatus("Running", [TimeSpan]::FromSeconds(30))
    } else {
        Write-Host "$Label service already running: $($service.Name)"
    }
}

function Stop-ServiceIfPresent {
    param(
        [Parameter(Mandatory = $true)][string]$Label,
        [Parameter(Mandatory = $true)][string[]]$Candidates
    )

    $service = Get-FirstExistingService -Candidates $Candidates
    if ($null -eq $service) {
        Write-Warning "$Label service not found. Checked: $($Candidates -join ', ')"
        return
    }

    if ($service.Status -ne "Stopped") {
        Write-Host "Stopping $Label service: $($service.Name)"
        Stop-Service -Name $service.Name
        $service.WaitForStatus("Stopped", [TimeSpan]::FromSeconds(30))
    } else {
        Write-Host "$Label service already stopped: $($service.Name)"
    }
}

function Show-DependencyPorts {
    Write-Host ""
    Write-Host "Port checks:"
    foreach ($entry in $Script:BackendDeps.Ports.GetEnumerator() | Sort-Object Name) {
        $open = Test-PortOpen -Port $entry.Value
        $wslOpen = $false
        if (($entry.Name -eq "Redis" -and $Script:BackendDeps.UseWslRedis) -or $entry.Name -eq "FlinkWebUI") {
            $wslOpen = Test-WslPortOpen -Port $entry.Value
        }
        $status = if ($open) {
            "OPEN"
        } elseif ($wslOpen) {
            "OPEN(WSL)"
        } else {
            "CLOSED"
        }
        Write-Host ("  {0,-22} {1,5}  {2}" -f $entry.Name, $entry.Value, $status)
    }
}
