

if __name__ == "__main__":
    f = open("mstime.txt",'r')
    mstime = int(f.read())
    f = open("tcptime.txt",'r')
    tcptime = int(f.read())
    overhead = ((mstime - tcptime)/tcptime) * 100
    # print("the overhead is :- " + str(overhead))
    if(overhead > 0 and overhead < 80):
        print(0)
    else:
        print(1)
