param(
    [switch]$Yes,
    [switch]$SkipRedis,
    [switch]$SkipRabbitMQ,
    [switch]$IncludeElasticsearchIndexes,
    [string]$ElasticsearchIndexPattern = "notes*",
    [switch]$IncludeMinioObjects
)

. "$PSScriptRoot\config.ps1"

function Confirm-DangerousAction {
    param(
        [Parameter(Mandatory = $true)][string]$Message
    )

    if ($Yes) {
        return $true
    }

    $answer = Read-Host "$Message Type YES to continue"
    return $answer -eq "YES"
}

Write-Host "This script clears cache-like local development data."
Write-Host "Default cleanup: Redis DB $($BackendDeps.RedisDatabase) and RabbitMQ queue messages."
Write-Host "It does NOT clear MySQL or MongoDB."
Write-Host ""

if (-not (Confirm-DangerousAction -Message "Clear local Redis/RabbitMQ cache data?")) {
    Write-Host "Canceled."
    exit 0
}

if (-not $SkipRedis) {
    Write-Host "Clearing Redis database $($BackendDeps.RedisDatabase)..."
    if ($BackendDeps.UseWslRedis) {
        wsl -d $BackendDeps.WslDistribution -- bash -lc "redis-cli -n $($BackendDeps.RedisDatabase) FLUSHDB"
    } else {
        redis-cli -n $BackendDeps.RedisDatabase FLUSHDB
    }

    if ($LASTEXITCODE -ne 0) {
        Write-Warning "Redis FLUSHDB failed. Make sure Redis is running and redis-cli is available."
    }
}

if (-not $SkipRabbitMQ) {
    $rabbitmqctl = Join-Path $BackendDeps.RabbitMQSbin "rabbitmqctl.bat"
    if (-not (Test-Path $rabbitmqctl)) {
        $rabbitmqctl = "rabbitmqctl"
    }

    Write-Host "Purging RabbitMQ queues in virtual host /..."
    $queueOutput = & $rabbitmqctl list_queues -p / name --quiet 2>$null
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "Could not list RabbitMQ queues. Make sure RabbitMQ is running and rabbitmqctl can authenticate."
    } else {
        $queues = $queueOutput | Where-Object { $_ -and $_.Trim() -ne "" }
        if (-not $queues) {
            Write-Host "No RabbitMQ queues to purge."
        } else {
            foreach ($queue in $queues) {
                $queueName = $queue.Trim()
                Write-Host "Purging queue: $queueName"
                & $rabbitmqctl purge_queue -p / $queueName | Out-Null
            }
        }
    }
}

if ($IncludeElasticsearchIndexes) {
    $message = "Delete Elasticsearch indices matching '$ElasticsearchIndexPattern' on localhost:$($BackendDeps.Ports.Elasticsearch)?"
    if (Confirm-DangerousAction -Message $message) {
        $uri = "http://127.0.0.1:$($BackendDeps.Ports.Elasticsearch)/$ElasticsearchIndexPattern"
        Write-Host "Deleting Elasticsearch indices: $uri"
        try {
            Invoke-RestMethod -Method Delete -Uri $uri | Out-Host
        } catch {
            Write-Warning "Elasticsearch delete failed: $($_.Exception.Message)"
        }
    }
}

if ($IncludeMinioObjects) {
    Write-Warning "MinIO object deletion is intentionally not automated here because it can remove uploaded files."
    Write-Host "Use the MinIO console at http://127.0.0.1:$($BackendDeps.Ports.MinIOConsole) to clear bucket '$($BackendDeps.MinioBucket)' manually."
}

Write-Host ""
Write-Host "Cache cleanup completed."
