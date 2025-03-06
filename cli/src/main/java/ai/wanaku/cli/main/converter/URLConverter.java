/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.wanaku.cli.main.converter;

import picocli.CommandLine;

import java.io.File;
import java.net.URL;

public class URLConverter implements CommandLine.ITypeConverter<URL> {

    /**
     *
     * @param value the command line argument String value
     * @return the URL object
     * @throws Exception if any error occurs during the conversion.
     */
    @Override
    public URL convert(String value) throws Exception {
        try {
            URL url = new URL(value);
            return url;
        } catch (Exception e){
            // so it's not an URL, maybe a local path?
        }
        // try if is a local path
        return new File(value).toURI().toURL();
    }
}
