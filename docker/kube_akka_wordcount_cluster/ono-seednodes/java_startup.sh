
#ip_addr=`ip addr |grep inet|grep -v 127.0.0.1 |awk  '{print $2}'|awk -F/ '{print $1}'`
#MY_POD_IP is defined in kube deployment
ip_addr=${MY_POD_IP}
java -cp /app/kube_akka_wordcount_cluster.jar -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:MaxRAMFraction=2  -Dono.cluster.seednodes.node1.hostname="${ip_addr}" -Dono.cluster.seednodes.node2.hostname="${ip_addr}"  udemy.cluster.onokube3.OnoClusterSeedNodes1
