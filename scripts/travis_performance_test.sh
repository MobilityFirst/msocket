cd .. &&
ant jar &&
cd scripts &&
javac -cp ../jars/msocket-1.0.0.jar:. runmsocketserverclient.java
java -cp ../jars/msocket-1.0.0.jar:. runmsocketserverclient > mstime.txt
javac runtcpserverclient.java;
java runtcpserverclient > tcptime.txt
head mstime.txt
head tcptime.txt
difference=`python3 check_performance_diff.py`
echo $difference
rm mstime.txt tcptime.txt *.class
cd ..
ant clean
rm build.number
if [[ $difference -eq 0 ]]
then
  exit 0
else
  exit 1
fi
