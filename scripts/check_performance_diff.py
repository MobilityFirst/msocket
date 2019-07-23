

if __name__ == "__main__":
    f = open("mstime.txt",'r')
    mstime = int(f.read())
    f = open("tcptime.txt",'r')
    tcptime = int(f.read())
    overhead = ((mstime - tcptime)/tcptime) * 100
    if(overhead > 0 and overhead < 50):
        print(1)
    else:
        print(0)
