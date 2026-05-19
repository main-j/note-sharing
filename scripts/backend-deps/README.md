# Backend Dependency Scripts

PowerShell scripts for local backend dependencies used by `Login_api`.

## Files

- `config.ps1` - shared paths, ports, credentials, and service name candidates.
- `start-backend-deps.ps1` - starts local dependencies.
- `stop-backend-deps.ps1` - safely stops local dependencies.
- `clear-backend-cache.ps1` - clears local cache-like data.

## Start Dependencies

Run from PowerShell:

```powershell
cd C:\Users\35445\Desktop\note-sharing\scripts\backend-deps
powershell -ExecutionPolicy Bypass -File .\start-backend-deps.ps1
```

The script tries to start:

- MySQL service
- MongoDB service
- Redis inside WSL `Ubuntu-24.04`
- RabbitMQ service
- Elasticsearch from `C:\programming\elasticsearch-9.4.1`
- MinIO from `C:\programming\minio`

### Apache Flink (optional)

`Login_api` depends on **Flink 1.19.3** as a library. The sample `BehaviorSearchJob` defaults to **`FLINK_EXECUTION_TARGET=remote`** (session cluster REST on **8081**). Use **`FLINK_EXECUTION_TARGET=local`** for an embedded MiniCluster only when you do not run a standalone cluster.

Official **Linux** `.tgz` builds **do not ship `start-cluster.bat` on Windows**. Use **WSL** and `bin/start-cluster.sh`, or run Flink fully inside Linux.

If you want a local **JobManager + TaskManager** (Web UI on port **8081**):

1. Download Flink **1.19.x** from [Apache Flink downloads](https://flink.apache.org/downloads.html) and extract to `C:\programming\flink-1.19.3` (or set `FlinkHome` in `config.ps1`).
2. In **Ubuntu (WSL)**, install JDK **17**: `sudo apt update && sudo apt install -y openjdk-17-jdk`
3. If `java` is not under `/usr/lib/jvm/java-17-openjdk-amd64`, run `dirname $(dirname $(readlink -f $(which java)))` and put that path in `FlinkWslJavaHome` in `config.ps1`, or clear `FlinkWslJavaHome` to use the default `java` on PATH.
4. Start everything **plus** Flink (scripts call WSL automatically when `.bat` is missing):

```powershell
powershell -ExecutionPolicy Bypass -File .\start-backend-deps.ps1 -StartFlinkCluster
```

Stop the cluster:

```powershell
powershell -ExecutionPolicy Bypass -File .\stop-backend-deps.ps1 -StopFlinkCluster
```

If running `BehaviorSearchJob` against this cluster you see **`Insufficient number of network buffers`**, the TaskManagers need more network memory. Append something like the following to **`$FLINK_HOME/conf/flink-conf.yaml`** (paths in WSL if the cluster runs under WSL), then restart the cluster:

```yaml
taskmanager.memory.process.size: 2048m
taskmanager.memory.network.min: 256m
taskmanager.memory.network.max: 512m
taskmanager.memory.network.fraction: 0.25
```

For **local** `FLINK_EXECUTION_TARGET=local`, the job applies similar defaults in code via MiniCluster configuration.

If your MinIO executable is still elsewhere, edit `config.ps1` and set `MinioExe`.

## Stop Dependencies

```powershell
cd C:\Users\35445\Desktop\note-sharing\scripts\backend-deps
powershell -ExecutionPolicy Bypass -File .\stop-backend-deps.ps1
```

You can skip specific components:

```powershell
powershell -ExecutionPolicy Bypass -File .\stop-backend-deps.ps1 -SkipMySQL -SkipMongoDB
powershell -ExecutionPolicy Bypass -File .\stop-backend-deps.ps1 -StopFlinkCluster
```

## Clear Cache Data

Default cleanup only clears:

- Redis database `0`
- RabbitMQ queue messages

It does not clear MySQL or MongoDB, and it does not delete MinIO objects by default.

Interactive:

```powershell
cd C:\Users\35445\Desktop\note-sharing\scripts\backend-deps
powershell -ExecutionPolicy Bypass -File .\clear-backend-cache.ps1
```

Non-interactive:

```powershell
powershell -ExecutionPolicy Bypass -File .\clear-backend-cache.ps1 -Yes
```

Optional Elasticsearch index deletion is disabled unless explicitly requested:

```powershell
powershell -ExecutionPolicy Bypass -File .\clear-backend-cache.ps1 -Yes -IncludeElasticsearchIndexes -ElasticsearchIndexPattern "notes*"
```

## Important Notes

- Keep the Elasticsearch and MinIO windows open while developing.
- If WSL asks for a sudo password when starting Redis, open Ubuntu and run `sudo service redis-server start` once manually.
- Run PowerShell as Administrator if a Windows service cannot be started or stopped.
- Update `config.ps1` whenever you move a dependency directory.
