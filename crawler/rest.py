#!/usr/bin/env python
import web
import subprocess
import os
import csv
import json

urls = (
    '/parse/(.*)', 'parse'
)

app = web.application(urls, globals())

class parse:
    def GET(self, usr_input):
        
        full_path = os.path.realpath(__file__)

        this_file_dir = os.path.dirname(full_path)

        args = usr_input.split('&')
        path = args[0]
        file_name = args[1]
        transfer_location = args[2]

        os.system("scp ~/" +path + " " + this_file_dir)
        json_data = {}
        with open(file_name) as csvfile:
            row_count = sum(1 for row in csvfile)
            json_data['row_count'] = row_count

        file_args = file_name.split('.')
        name_wo_extension = file_args[0]
        name_as_json = name_wo_extension + '.json'
        with open(name_as_json, 'w') as outfile:
            json.dump(json_data, outfile)
        os.system("scp " + name_as_json + " " + transfer_location)
        return json.dumps(json_data)

if __name__ == "__main__":
    app.run()