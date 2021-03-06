/*
 * Copyright (c) 2010-2015 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.cli.seppuku.command;

import com.beust.jcommander.Parameter;
import com.evolveum.midpoint.cli.common.UrlConverter;

import java.net.URL;

/**
 * @author Viliam Repan (lazyman)
 */
public class Command {

    public static final String P_HELP = "-h";
    public static final String P_HELP_LONG = "--help";

    public static final String P_VERBOSE = "-v";
    public static final String P_VERBOSE_LONG = "--verbose";

    public static final String P_URL = "-U";
    public static final String P_URL_LONG = "--url";

    public static final String P_USERNAME = "-u";
    public static final String P_USERNAME_LONG = "--username";

    public static final String P_PASSWORD = "-p";
    public static final String P_PASSWORD_LONG = "--password";

    public static final String P_ASK_PASSWORD = "-P";
    public static final String P_ASK_PASSWORD_LONG = "--password-ask";

    public static final String P_MIDPOINT_HOME = "-m";
    public static final String P_MIDPOINT_HOME_LONG = "--midpoint-home";

    @Parameter(names = {P_HELP, P_HELP_LONG}, help = true,
            description = "Print this help")
    private boolean help = false;

    @Parameter(names = {P_VERBOSE, P_VERBOSE_LONG},
            description = "Verbose output")
    private boolean verbose = false;

    /**
     * OPTIONS FOR JDBC CLIENT CONNECTION
     */
    @Parameter(names = {P_URL, P_URL_LONG}, converter = UrlConverter.class,
            validateWith = UrlConverter.class, required = true,
            description = "Url to MidPoint model webservice endpoint")
    private URL url;

    @Parameter(names = {P_USERNAME, P_USERNAME_LONG}, required = true,
            description = "Username for MidPoint webservice login")
    private String username;

    @Parameter(names = {P_PASSWORD, P_PASSWORD_LONG},
            description = "Password for MidPoint webservice login")
    private String password;

    @Parameter(names = {P_ASK_PASSWORD, P_ASK_PASSWORD_LONG}, password = true,
            echoInput = true, description = "Password for MidPoint webservice login")
    private String askPassword;

    @Parameter(names = {P_MIDPOINT_HOME, P_MIDPOINT_HOME_LONG},
            description = "Path to MidPoint home folder")
    private String midpointHome;

    public String getAskPassword() {
        return askPassword;
    }

    public boolean isHelp() {
        return help;
    }

    public String getPassword() {
        return password;
    }

    public URL getUrl() {
        return url;
    }

    public String getUsername() {
        return username;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public String getMidpointHome() {
        return midpointHome;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Command{");
        sb.append("askPassword='").append(askPassword).append('\'');
        sb.append(", help=").append(help);
        sb.append(", verbose=").append(verbose);
        sb.append(", url=").append(url);
        sb.append(", username='").append(username).append('\'');
        sb.append(", password='").append(password).append('\'');
        sb.append(", midpointHome='").append(password).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
