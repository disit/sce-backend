/* Smart Cloud/City Engine backend (SCE).
   Copyright (C) 2015 DISIT Lab http://www.disit.org - University of Florence

   This program is free software; you can redistribute it and/or
   modify it under the terms of the GNU General Public License
   as published by the Free Software Foundation; either version 2
   of the License, or (at your option) any later version.
   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.
   You should have received a copy of the GNU General Public License
   along with this program; if not, write to the Free Software
   Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA. */

package sce;

import java.net.URLConnection;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 *
 * @author Daniele Cenni, daniele.cenni@unifi.it
 *
 */
public class ReadURL implements Runnable {

    Thread parent;
    URLConnection con;
    String result = null;

    public ReadURL(Thread parent, URLConnection con) {
        this.parent = parent;
        this.con = con;
    }

    public void run() {
        this.result = getUrlContents(con);
        //System.out.println("Timer thread forcing parent to quit connection");
        ((HttpURLConnection) con).disconnect();
        //System.out.println("Timer thread closed connection held by parent, exiting");
    }
    
    private String getUrlContents(URLConnection urlConnection) {
        StringBuilder content = new StringBuilder();
        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                content.append(line);
            }
            bufferedReader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return content.toString();
    }

    public String getResult() {
        return this.result;
    }
}
