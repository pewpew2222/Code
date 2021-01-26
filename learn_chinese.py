import codecs, json, pprint, random, time


def main():
      
      

      # Imports dictionary and translations needed for the program
      with codecs.open('dict_chinese.json', 'r', 'utf-8') as json_file:
            dictionary_chinese = json.load(json_file, encoding = 'utf-8')


      # Assigns types for the different translations in the dictionary
      types = {
            #"trad"    : "the traditional chinese",
            "simp"    : "the simplified chinese",
            #"pinyin"  : "the pinyin",
      }


      # Creates a function for the user to choose the amount of questions asked in the quiz
      def user():
            characters = len(dictionary_chinese)
            print("There are currently", characters, "available characters in the dictionary.")
            questions = int(input("How many questions?\n: "))
            return questions


      # Creates a function that gives out questions to the user
      def quiz (iterations):
            # Set questions to a viable amount
            if iterations > len(dictionary_chinese):
                  iterations = len(dictionary_chinese)
            
            # Copy the right amount of questions from the dictionary to a temporary list inside the function
            list_questions = random.sample(dictionary_chinese, iterations)
            
            # Refer back to the temp list and choose a random question and type from that which is provided in the temp list
            # For each iteration a new elem is chosen, and with that a new word. A new word is never chosen within the loop, but outside.
            for elem in list_questions:
                  keylist = types.keys()
                  type = random.choice(list(keylist))
                  print("What does ", types[type],' ',elem[type],'(',elem['pinyin'],") translate to in English?", sep = '')

                  
                  # Creates a dict with 4 random elem from list_questions and adds the 5th elem 'answer5' as the correct answer
                  #query_index = random.sample(range(0,len(dictionary_chinese)-1), iterations)
                  # for index in query_index:
                  #       print("Query: "+dictionary_chinese[index]['pinyin'])
                  #       print("Answer: "+dictionary_chinese[index]['meaning'])
                  
                  d_answer = {}
                  for i in range(1,5):
                        d_answer["answer{0}".format(i)] = random.choice(list_questions)["meaning"]
                  d_answer["answer5"] = elem["meaning"]


                  answer_list = []
                  for value in d_answer.values():
                        answer_list.append(value)
                  
            
                  random.shuffle(answer_list)
                  answer_list_temp = answer_list.copy()
                  
                  
                  print('1 | ', answer_list_temp.pop(0), "\n",
                        '2 | ', answer_list_temp.pop(0), "\n",
                        '3 | ', answer_list_temp.pop(0), "\n",
                        '4 | ', answer_list_temp.pop(0), "\n",
                        '5 | ', answer_list_temp.pop(0), sep = '')
                  
                  # User inputs answer
                  user_answer = int(input("Your answer: "))-1


                  # Give the correct answer to the user
                  print(elem[type],' (',elem["pinyin"],") means \n\t",elem["meaning"], sep = '')
                  

                  if answer_list[user_answer] == elem['meaning']:
                        print('Correct!\n')
                  else:
                        print('Incorrect!\n')
                  
                  time.sleep(.5)
                  # After giving an answer the function will repeat itself and ask a new question


      
      # Run the quiz with data collected from user input, which defines the amount of questions given
      quiz(user())

# Run the main function (the whole thing!)
main()