
# coding: utf-8

# In[94]:

import utils
import models as md
import csv
from sklearn.feature_extraction import DictVectorizer
from sklearn import datasets
from sklearn.utils import shuffle
from sklearn.ensemble import BaggingClassifier
from sklearn.neighbors import KNeighborsClassifier
from sklearn.naive_bayes import GaussianNB
from sklearn.ensemble import RandomForestClassifier, AdaBoostClassifier
from sklearn.decomposition import RandomizedPCA
from sklearn import tree
from sklearn import cross_validation
from sklearn import svm
from sklearn.decomposition import PCA
from sklearn import datasets
from sklearn.tree import DecisionTreeClassifier
from sklearn.metrics import zero_one_loss
from sklearn.ensemble import AdaBoostClassifier
from sklearn.feature_selection import VarianceThreshold
from sklearn.feature_selection import SelectKBest
from sklearn.feature_selection import chi2
from sklearn.feature_selection import VarianceThreshold
from sklearn.linear_model import LogisticRegression
from sklearn.tree import DecisionTreeClassifier

import time


import os
#Note: You can reuse code that you wrote in etl.py and models.py and cross.py over here. It might help.


# In[95]:

RANDOM_STATE = 545510477


# In[96]:

def silentremove(filename):
    """ Copied from the internet. """
    try:
        os.remove(filename)
    except OSError as e: # this would be "except OSError, e:" before Python 2.6
        if e.errno != errno.ENOENT: # errno.ENOENT = no such file or directory
            raise # re-raise exception if a different error occured


# In[97]:

x_libsvm, y = datasets.load_svmlight_file('spark_output/patient_vector.libsvm')

#print()
d1 = {}

with open('spark_output/patient_target.csv') as csvfile:
    for row in csv.reader(csvfile):
        d1[int(row[0])] = int(row[1])

#print(d1)

x = x_libsvm.toarray()
print(x.shape)
#print(x)
d2 = {}

for i in range(0, len(x)):
    d2[int(y[i])] = list(x[i])

#print(d2)
d3 = []

with open('kaggle/septic-shock-patients.csv') as csvfile:
    for row in csv.DictReader(csvfile):
        d3.append(int(row['Id']))
        
#print(d3)

x_train = []
y_train = []

x_test = []
y_test = []
key_ordering = []

for key in d1:
    if(key in d3):
        y_test.append(d1[key])
        x_test.append(d2[key])
        key_ordering.append(key)
    else:
        y_train.append(d1[key])
        x_train.append(d2[key])
        
#print(x_train)
#print(x_train)
#print(y_train)
#print(key_ordering)    
    


# In[106]:

def generate_ret(key_ordering, pred, d3):
    d = open('output_kaggle', 'w')
    dc = {}
    for i in range(0, len(key_ordering)):
        dc[str(key_ordering[i])] = str(pred[i])
        
    #print(dc)
    for i in range(0, len(d3)):
        #print(str(d3[i]))
        if(str(d3[i]) in dc):
            d.write(str(d3[i]) + "," + dc[str(d3[i])] + "\n")
        else:
            d.write(str(d3[i]) + ',' + '0' + "\n")
    d.close()


def generate_ret2(key_ordering, pred, d3):
    d = open('output_kaggle', 'w')
    dc = {}
    for i in range(0, len(key_ordering)):
        dc[str(key_ordering[i])] = str(pred[i])
        
    print(dc)
    for i in range(0, len(d3)):
        #print(str(d3[i]))
        if(str(d3[i]) in dc):
            d.write(dc[str(d3[i])] + "\n")
        else:
            d.write('1' + "\n")
    d.close()
    


# In[ ]:




# In[ ]:




# In[103]:

def testing_helper_kaggle(X_train, Y_train):
    #orderlist = X_train.todense()
    
    n_estimators = 75
    # A learning rate of 1. may not be optimal for both SAMME and SAMME.R
    learning_rate = 0.67
    #sel = VarianceThreshold(threshold=(0.00001))
    #X_train = sel.fit_transform(X_train)
    #X_train = SelectKBest(chi2, k=100).fit_transform(X_train, Y_train)
    
    dt_stump = DecisionTreeClassifier(max_depth=10, min_samples_leaf=1)
    dt_stump.fit(X_train, Y_train)
    
    clf = AdaBoostClassifier(base_estimator=dt_stump,
                            learning_rate=learning_rate,
                            n_estimators=n_estimators,
                            algorithm="SAMME")
    X_train, X_test, Y_train, y_test = cross_validation.train_test_split(X_train, Y_train, test_size=0.3)
    
    clf = clf.fit(X_train, Y_train)
    
    Y_pred = clf.predict(X_test)  
    print(md.display_metrics("hi", Y_pred, y_test))

    
testing_helper_kaggle(x_train, y_train)    


# In[104]:

'''
You can use any model you wish.

input: X_train, Y_train, X_test
output: Y_pred
'''
def my_classifier_predictions(X_train,Y_train,X_test):
    #TODO: complete this
    clf = DecisionTreeClassifier(random_state=RANDOM_STATE, max_depth=5)
    clf.fit(X_train, Y_train)   
    Y_pred = clf.predict(X_test)  

    return Y_pred






# In[105]:

def main():
	_pred = my_classifier_predictions(x_train,y_train,x_test)
	generate_ret(key_ordering, _pred, d3)
	#The above function will generate a csv file of (patient_id,predicted label) and will be saved as "my_predictions.csv" in the deliverables folder.

if __name__ == "__main__":
    main()


# In[ ]:



