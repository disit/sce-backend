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

import java.io.IOException;
import java.util.Properties;
import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;

public class Mail {

    public static void sendMail(String subject, String message, String recipient, String headerName, String headerValue) {
        try {
            //load mail settings from quartz.properties
            Properties prop = new Properties();
            prop.load(Mail.class.getResourceAsStream("quartz.properties"));
            String smtp_hostname = prop.getProperty("smtp_hostname");
            int smtp_port = Integer.parseInt(prop.getProperty("smtp_port"));
            boolean smtp_ssl = prop.getProperty("smtp_ssl").equalsIgnoreCase("true");
            String smtp_username = prop.getProperty("smtp_username");
            String smtp_password = prop.getProperty("smtp_password");
            String smtp_mailfrom = prop.getProperty("smtp_mailfrom");

            Email email = new SimpleEmail();
            email.setHostName(smtp_hostname);
            email.setSmtpPort(smtp_port);
            email.setAuthenticator(new DefaultAuthenticator(smtp_username, smtp_password));
            email.setSSLOnConnect(smtp_ssl);
            email.setFrom(smtp_mailfrom);
            email.setSubject(subject);
            email.setMsg(message);
            String[] recipients = recipient.split(";"); //this is semicolon separated array of recipients
            for (String tmp : recipients) {
                email.addTo(tmp);
            }
            //email.addHeader(headerName, headerValue);
            email.send();
        } catch (EmailException e) {
        } catch (IOException e) {
        }
    }
}
