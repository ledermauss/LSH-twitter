def get_set(k):
    with open(k, "r") as f:
        raul = set()
        for line in f:
            split = line.split(",")
            if len(split) > 2 and float(split[2]) > 0.9:  # prevent \n
                raul.add((int(split[0]), int(split[1])))
    return raul

brute = get_set('brute/brute_30k.csv')


def get_params(name):
    print(name)
    lsh = get_set(name)
    TruePos = 0
    FalsePos = 0
    TrueNeg = 0
    FalseNeg = 0
    TotalPos = 0.1
    TotalNeg = 0.1
    LabelPos = 0
    LabelNeg = 0


    for i in range(30000):
        if i % 5000 == 0:
            print("Already at: {}".format(i))
        for j in range(i, 30000):
            if (i, j) in brute:
                TotalPos = TotalPos + 1
                if (i, j) in lsh:  # in both
                    TruePos = TruePos + 1
                    LabelPos = LabelPos + 1
                else:  # in brute, not in my classif
                    FalseNeg = FalseNeg + 1
                    LabelNeg = LabelNeg + 1
            else:
                TotalNeg = TotalNeg + 1
                if (i, j) in lsh:
                    FalsePos = FalsePos + 1
                    LabelPos = LabelPos + 1
                else:
                    TrueNeg = TrueNeg + 1
                    LabelNeg = LabelNeg + 1
    print("TP: {}, FN: {}, FP: {}, TN: {},"
          " Total Pos : {}, Total Neg: {}".format(TruePos, FalseNeg, FalsePos, TrueNeg, TotalPos, TotalNeg))
    print("TPRate: {}, FPRate: {}".format(TruePos/TotalPos, FalsePos/TotalNeg))
    return


files = ["rows/rows_2.csv",  "rows/rows_4_bands_15.csv", "rows/rows_5.csv",
         "rows/rows_10.csv", "rows/rows_15.csv", "rows/rows_20.csv", "rows/rows_30.csv"]

for f in files:
    get_params(f)



