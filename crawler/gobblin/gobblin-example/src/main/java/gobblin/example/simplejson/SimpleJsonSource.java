/* (c) 2014 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 */

package gobblin.example.simplejson;

import java.io.IOException;
import java.io.File;
import java.io.FileWriter;

import java.util.List;
import java.util.Iterator;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

import gobblin.configuration.ConfigurationKeys;
import gobblin.configuration.SourceState;
import gobblin.configuration.WorkUnitState;
import gobblin.source.Source;
import gobblin.source.extractor.Extractor;
import gobblin.source.workunit.Extract;
import gobblin.source.workunit.WorkUnit;

import org.apache.commons.io.FileUtils;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * An implementation of {@link Source} for the simple JSON example.
 *
 * <p>
 *   This source creates one {@link gobblin.source.workunit.WorkUnit}
 *   for each file to pull and uses the {@link SimpleJsonExtractor} to pull the data.
 * </p>
 *
 * @author ynli
 */
@SuppressWarnings("unused")
public class SimpleJsonSource implements Source<String, String> {

  private static final String SOURCE_FILE_KEY = "source.file";

  @Override
  public List<WorkUnit> getWorkunits(SourceState state) {
    List<WorkUnit> workUnits = Lists.newArrayList();

    if (!state.contains(ConfigurationKeys.SOURCE_FILEBASED_FILES_TO_PULL)) {
      return workUnits;
    }

    // Create a single snapshot-type extract for all files
    Extract extract = new Extract(state, Extract.TableType.SNAPSHOT_ONLY,
        state.getProp(ConfigurationKeys.EXTRACT_NAMESPACE_NAME_KEY, "ExampleNamespace"), "ExampleTable");

    String filesToPull = state.getProp(ConfigurationKeys.SOURCE_FILEBASED_FILES_TO_PULL);
    File tempFileDir = new File("test_temp/"); // TODO: Delete the dir after completion.
    tempFileDir.mkdir();
    String tempFileDirAbsolute = "";
    try{
    	tempFileDirAbsolute = tempFileDir.getCanonicalPath(); // Retrieve absolute path of temp folder
    } catch(IOException e){
        e.printStackTrace();
    }

    try{
      String intermediateName = "/generated.json";
      String finalName = tempFileDirAbsolute + intermediateName;
      FileWriter generated = new FileWriter(finalName);

      for (String file : Splitter.on(',').omitEmptyStrings().split(filesToPull)) {
        Iterator it = FileUtils.iterateFiles(new File(file), null, true);
        while(it.hasNext()) {
          File newFile = (File) it.next();
          String basePath = newFile.getCanonicalPath(); // Retrieve absolute path of source
          Path path = newFile.toPath();

          //creating intermediate JSON
          JSONObject intermediate = new JSONObject();
          intermediate.put("creationTime", String.valueOf(attr.creationTime()));
          intermediate.put("lastAccessTime", String.valueOf(attr.lastAccessTime()));
          intermediate.put("lastModifiedTime", String.valueOf(attr.lastModifiedTime()));
          intermediate.put("isDirectory", String.valueOf(attr.isDirectory()));
          intermediate.put("isOther", String.valueOf(attr.isOther()));
          intermediate.put("isRegularFile", String.valueOf(attr.isRegularFile()));
          intermediate.put("isSymbolicLink", String.valueOf(attr.isSymbolicLink()));
          intermediate.put("size", String.valueOf(attr.size()));
          intermediate.put("filename", "" + newFile);

          // Create intermediate temp file
          generated.write(intermediate.toJSONString() + "\n");

        } // end while
      } // end for

      generated.flush();
      generated.close();

      // Create one work unit for each file to pull
      WorkUnit workUnit = new WorkUnit(state, extract);
      workUnit.setProp(SOURCE_FILE_KEY, finalName);
      workUnits.add(workUnit);

      System.out.println(" ");
      System.out.println("----END----");

    } catch(IOException e){
        e.printStackTrace();
    }

    return workUnits;
  }

  @Override
  public Extractor<String, String> getExtractor(WorkUnitState state)
      throws IOException {
    return new SimpleJsonExtractor(state);
  }

  @Override
  public void shutdown(SourceState state) {
    // Nothing to do
  }
}
