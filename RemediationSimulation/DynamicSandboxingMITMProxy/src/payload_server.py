import os
from flask import Flask, send_from_directory

app = Flask(__name__, template_folder='../templates')


@app.route("/original",methods=['GET'])
def hello_world():
    dic = os.getcwd()
    dic = os.path.dirname(dic)
    print(dic)
    return send_from_directory(dic, 'original.zip', as_attachment=True)
# send_from_directory(dic, 'ATGtest1.php.htm.zip', as_attachment=True)

@app.route("/new",methods=['GET'])
def hello_world2():
    dic = os.getcwd()
    dic = os.path.dirname(dic)
    print(dic)
    return send_from_directory(dic, 'ATGtest1.php.htm.zip', as_attachment=True)


@app.route("/script.zip",methods=['GET'])
def hello_world3():
    dic = os.getcwd()
    dic = os.path.dirname(dic)
    print(dic)
    return send_from_directory(dic, 'ATGtest1.php.htm.zip', as_attachment=True)







if __name__ == '__main__':
    app.run(port=1551, host='0.0.0.0')
