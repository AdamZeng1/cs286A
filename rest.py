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
        # with open(file_name) as csvfile:
        #     row_count = sum(1 for row in csvfile)
        #     json_data['row_count'] = row_count


        delimiter = ','
        quote_character = '"'

        csv_fp = open(file_name, 'rb')
        csv_reader = csv.reader(csv_fp, delimiter=delimiter)

        current_row = 0
        header = ""
        for row in csv_reader:
            current_row += 1

            if current_row == 1: # Skip heading row
                header = row

        json_data['row_count'] = current_row
        json_data['header'] = header






        file_args = file_name.split('.')
        name_wo_extension = file_args[0]
        name_as_json = name_wo_extension + '.json'
        with open(name_as_json, 'w') as outfile:
            json.dump(json_data, outfile)
        os.system("scp " + name_as_json + " " + this_file_dir + "/crawler/gobblin/test_source/")
        return json.dumps(json_data)


if __name__ == "__main__":
    app = MyApplication(urls, globals())
    app.run()