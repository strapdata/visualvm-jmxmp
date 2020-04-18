
package com.strapdata.nbjmxmp;

import javax.management.remote.JMXConnector;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.jmxmp.JMXMPConnector;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;

/**
 * Overwrite the default JMXMP provider to inject the TLS SASL profile with CallbackHandler
 */
public class SecuredJmxmpClientProvider extends com.sun.jmx.remote.protocol.jmxmp.ClientProvider {

    public SecuredJmxmpClientProvider() {
        super();
    }

    @Override
    public JMXConnector newJMXConnector(JMXServiceURL serviceURL, Map environment) throws IOException {
        if (!serviceURL.getProtocol().equals("jmxmp")) {
            throw new MalformedURLException("Protocol not jmxmp: " + serviceURL.getProtocol());
        } else {
            Map env = new HashMap();
            env.putAll(environment);
            Security.addProvider(new com.sun.security.sasl.Provider());
            env.put("jmx.remote.profiles", "TLS SASL/PLAIN");

            String username = System.getProperty("jmxmp.username","cassandra");
            String password = System.getProperty("jmxmp.password","cassandra");
            if (environment.containsKey("jmx.remote.credentials")) {
                String[] creds = (String[]) environment.get("jmx.remote.credentials");
                username = creds[0];
                password = creds[1];
            }
            env.put("jmx.remote.sasl.callback.handler", new UserPasswordCallbackHandler(username, password));
            //System.out.println("JMXMP user="+username+" password="+password+" env="+env);
            return new JMXMPConnector(serviceURL, env);
        }
    }

    static class UserPasswordCallbackHandler implements javax.security.auth.callback.CallbackHandler
    {
        private String username;
        private char[] password;

        public UserPasswordCallbackHandler(String user, String password)
        {
            this.username = user;
            this.password = (password == null) ? null : password.toCharArray();
        }

        public void handle(javax.security.auth.callback.Callback[] callbacks)
                throws IOException, javax.security.auth.callback.UnsupportedCallbackException
        {
            for (int i = 0; i < callbacks.length; i++) {
                if (callbacks[i] instanceof PasswordCallback)
                {
                    PasswordCallback pcb = (PasswordCallback) callbacks[i];
                    pcb.setPassword(password);
                } else if (callbacks[i] instanceof javax.security.auth.callback.NameCallback)
                {
                    NameCallback ncb = (NameCallback) callbacks[i];
                    ncb.setName(username);
                } else {
                    throw new UnsupportedCallbackException(callbacks[i]);
                }
            }
        }

        private void clearPassword()
        {
            if (password != null) {
                for (int i = 0; i < password.length; i++)
                    password[i] = 0;
                password = null;
            }
        }

        protected void finalize()
        {
            clearPassword();
        }
    }
}
