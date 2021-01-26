# importing libraries 
from PyQt5.QtWidgets import * 
from PyQt5.QtGui import * 
from PyQt5.QtCore import * 
import sys, random, json, pprint, codecs, time

  
with codecs.open('dict_chinese.json', 'r', 'utf-8') as json_file:
            dictionary_chinese = json.load(json_file, encoding = 'utf-8')
types = {
            #"trad"    : "the traditional kanji",
            "simp"    : "the simplified kanji",
            #"pinyin"  : "the pinyin",
      }


def questions():
      characters = len(dictionary_chinese)
      print("There are currently", characters, "available characters in the dictionary.")
      questions = int(input("How many questions?\n: "))
      return questions


def quiz(questions):
      # Set amount of questions to a viable amount
      if questions > len(dictionary_chinese):
            questions = len(dictionary_chinese)
            
            # Copy the right amount of questions from the dictionary to a temporary list inside the function
      list_questions = random.sample(dictionary_chinese, questions)
            
            
      for elem in list_questions:
            keylist = types.keys()
            type = random.choice(list(keylist))
            print("What does ", types[type],' ',elem[type],'(',elem['pinyin'],") translate to in English?", sep = '')


            d_answer = {}
            for i in range(1,5):
                  d_answer["answer{0}".format(i)] = random.choice(list_questions)["meaning"]
            d_answer["answer4"] = elem["meaning"]


            answer_list = []
            for value in d_answer.values():
                  answer_list.append(value)
                  
            
            random.shuffle(answer_list)
            answer_list_temp = answer_list.copy()
                  

            print('1 | ', answer_list_temp.pop(0), "\n",
                  '2 | ', answer_list_temp.pop(0), "\n",
                  '3 | ', answer_list_temp.pop(0), "\n",
                  '4 | ', answer_list_temp.pop(0), sep = '')
                  
            # User inputs answer
            user_answer = int(input("Your answer: "))-1


            # Give the correct answer to the user
            print(elem[type],' (',elem["pinyin"],") means \n\t",elem["meaning"], sep = '')
                  

            if answer_list[user_answer] == elem['meaning']:
                  print('Correct!\n')
            else:
                  print('Incorrect!\n')
                  
            time.sleep(.5)


class Window(QMainWindow): 
      def __init__(self): 
            super().__init__() 
            #questions()
            # setting title 
            self.setWindowTitle("Learn Chinese by Sixten") 
  
            # setting geometry 
            self.setGeometry(400, 500, 600, 400) 
            self.setFixedSize(QSize(600,400))
        
            self.label = QLabel("电脑", self)
            #labelfont = label.font()
            #labelfont.setPointSize(40)
            self.label.setFont(QFont('Arial', 40))
            self.label.setGeometry(0,100,600,100)
            self.label.setAlignment(Qt.AlignCenter)
            #label.adjustSize() 
            self.label.setStyleSheet("QLabel {background-color: red;}")

            self.labelUp = QLabel("Welcome!", self)
            self.labelUp.setFont(QFont('Arial', 30))
            self.labelUp.setGeometry(0,0,500,100)
            self.labelUp.setAlignment(Qt.AlignCenter) 
            self.labelUp.setStyleSheet("QLabel {background-color: purple;}")
            

            self.labelCorner = QLabel("by Sixten", self)
            self.labelCorner.move(2, -9)

            #quiz(questions)


            # calling method

  
            # showing all the widgets 

  
      # method for widgets
  
            # creating a push button 
        
            self.button1 = QPushButton("CLICK\nheey", self) 
            self.button2 = QPushButton("CLICK", self) 
            self.button3 = QPushButton("CLICK", self) 
            self.button4 = QPushButton("CLICK", self)
            self.buttonnext = QPushButton("Next", self) 
        

  
            # setting geometry of button 
            #label.move(100, 200)
            self.button1.setGeometry(0, 200, 300, 100) 
            self.button2.setGeometry(0, 300, 300, 100)
            self.button3.setGeometry(300, 200, 300, 100)
            self.button4.setGeometry(300, 300, 300, 100)
            self.buttonnext.setGeometry(500, 0, 100, 100)


        


            # adding action to a button 
            self.button1.clicked.connect(self.option1)
            self.button2.clicked.connect(self.option2)
            self.button3.clicked.connect(self.option3)
            self.button4.clicked.connect(self.option4)


      def option1(self, label): 
            self.label.setText('meaning')
      def option2(self): 
            print('1')
      def option3(self):
            print('1')
      def option4(self):
            print('1')


  
  
    # action method  


app = QApplication(sys.argv)

window = Window()
window.show()

app.exec_()