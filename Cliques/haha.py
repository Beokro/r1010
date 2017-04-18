fr = open('haha', 'r')
fw = open('haha.txt', 'w')
def translate(st):
    result = ""
    for item in st:
        if item.isdigit():
            result += chr(int(item) + 97)
        else:
            result += str(ord(item) - 97)
    return result
index = 0
for line in fr:
    split = line.split()
    for i in range(index + 1, 20):
        if split[i] == '1':
            fw.write(str(i) + '\t' + str(index))
        else:
            fw.write(translate(str(i)) + '\t' + translate(str(index)))
        fw.write('\n')
    index = index + 1

fr.close()
fw.close()
