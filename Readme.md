# Introduction

This project is a boilerplate project for scala/sbt, which provides 4 subprojects 1) model 2) dao 3) service 4) web. Currently, there is no dependency for each sub-project but you can add easily. The dependency graph of the sub-projects as follow: web -> service -> dao -> model.

# Build

```bash
sbt compile
```

# Test

```bash
sbt model/run
```

```bash
sbt dao/run
```

```bash
sbt service/run
```

```bash
sbt web/run
```
# akka_training



# Open ports

add port definitions in /etc/pf.conf like the statement below.

```
pass in inet proto tcp from 192.168.0.0/24 to any port 8000
```


and run the command below

```
sudp pfctl -f /etc/pf.conf
```

