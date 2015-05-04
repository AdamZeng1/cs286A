#!/usr/bin/env python
import web
import subprocess
import os
import csv
import json

urls = (
    '/(.*)', 'parse'
)

class MyApplication(web.application):
    def run(self, port=8080, *middleware):
        func = self.wsgifunc(*middleware)
        return web.httpserver.runsimple(func, ('localhost', port))

class parse:
    def GET(self, usr_input):
        
        full_path = os.path.realpath(__file__)

        this_file_dir = os.path.dirname(full_path)

        args = usr_input.split('/')
        file_name = args[len(args)-1]


        os.system("scp /"+usr_input + " " + this_file_dir)
        json_data = {}
        with open(file_name) as csvfile:
            row_count = sum(1 for row in csvfile)
            json_data['row_count'] = row_count

        file_args = file_name.split('.')
        name_wo_extension = file_args[0]
        name_as_json = name_wo_extension + '.json'
        with open(name_as_json, 'w') as outfile:
            json.dump(json_data, outfile)
        os.system("scp " + name_as_json + " " + this_file_dir + "/crawler/gobblin/test_source/")
        return "scp " + name_as_json + " " + this_file_dir + "/crawler/gobblin/test_source/"


if __name__ == "__main__":
    app = MyApplication(urls, globals())
    app.run()