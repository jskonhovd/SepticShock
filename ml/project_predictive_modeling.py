
# coding: utf-8

# In[35]:

import numpy as np
from sklearn.datasets import load_svmlight_file
from sklearn.linear_model import LogisticRegression
from sklearn.svm import LinearSVC
from sklearn.tree import DecisionTreeClassifier
from sklearn.metrics import *
from sklearn import datasets
from sklearn.utils import shuffle
import utils


# In[28]:

# USE THIS RANDOM STATE FOR ALL OF THE PREDICTIVE MODELS
# THE TESTS WILL NEVER PASS
RANDOM_STATE = 545510477


# In[29]:

#input: X_train, Y_train and X_test
#output: Y_pred
def logistic_regression_pred(X_train, Y_train, X_test):
    #TODO: train a logistic regression classifier using X_train and Y_train. Use this to predict labels of X_test
    #use default params for the classifier	
    
    logreg = LogisticRegression(random_state=RANDOM_STATE)
    logreg.fit(X_train, Y_train)
    Y_pred = logreg.predict(X_test)

    
    return Y_pred


# In[30]:

#input: X_train, Y_train and X_test
#output: Y_pred
def svm_pred(X_train, Y_train, X_test):
    #TODO:train a SVM classifier using X_train and Y_train. Use this to predict labels of X_test
    #use default params for the classifier
    C = 1.0
    lin_svc = LinearSVC(random_state=RANDOM_STATE)    
    lin_svc.fit(X_train,Y_train) 
    Y_pred = lin_svc.predict(X_test)
    return Y_pred


# In[31]:

#input: X_train, Y_train and X_test
#output: Y_pred
def decisionTree_pred(X_train, Y_train, X_test):
    #TODO:train a logistic regression classifier using X_train and Y_train. Use this to predict labels of X_test
    #IMPORTANT: use max_depth as 5. Else your test cases might fail.
    
    clf = DecisionTreeClassifier(random_state=RANDOM_STATE, max_depth=5)
    clf.fit(X_train, Y_train)   
    Y_pred = clf.predict(X_test)   
    return Y_pred


# In[32]:

#input: Y_pred,Y_true
#output: accuracy, auc, precision, recall, f1-score
def classification_metrics(Y_pred, Y_true):
    #TODO: Calculate the above mentioned metrics
    #NOTE: It is important to provide the output in the same order
    acc = accuracy_score(Y_pred, Y_true)
    auc = roc_auc_score(Y_true, Y_pred)
    precision = precision_score(Y_true, Y_pred)
    recall = recall_score(Y_true, Y_pred)
    f1score = f1_score(Y_true, Y_pred)
    return acc,auc,precision,recall,f1score


# In[33]:

#input: Name of classifier, predicted labels, actual labels
def display_metrics(classifierName,Y_pred,Y_true):
    print "______________________________________________"
    print "Classifier: "+classifierName
    acc, auc_, precision, recall, f1score = classification_metrics(Y_pred,Y_true)
    print "Accuracy: "+str(acc)
    print "AUC: "+str(auc_)
    print "Precision: "+str(precision)
    print "Recall: "+str(recall)
    print "F1-score: "+str(f1score)
    print "______________________________________________"
    print ""


# In[36]:

def main():
    x_libsvm,y_libsvm = datasets.load_svmlight_file('spark_output/scaled_target_vector.libsvm')
    X, y = shuffle(x_libsvm.todense(), y_libsvm)
    offset = int(0.7*len(X))
    X_train, Y_train = X[:offset], y[:offset]
    X_test, Y_test = X[offset:], y[offset:]
    display_metrics("Logistic Regression",logistic_regression_pred(X_train,Y_train,X_test),Y_test)
    display_metrics("SVM",svm_pred(X_train,Y_train,X_test),Y_test)
    display_metrics("Decision Tree",decisionTree_pred(X_train,Y_train,X_test),Y_test)

if __name__ == "__main__":
	main()


# In[ ]:



