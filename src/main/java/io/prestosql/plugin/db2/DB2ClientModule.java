/*
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
package io.prestosql.plugin.db2;

import com.ibm.db2.jcc.DB2Driver;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import io.prestosql.plugin.jdbc.BaseJdbcConfig;
import io.prestosql.plugin.jdbc.ConnectionFactory;
import io.prestosql.plugin.jdbc.DecimalConfig;
import io.prestosql.plugin.jdbc.DecimalSessionPropertiesProvider;
import io.prestosql.plugin.jdbc.DriverConnectionFactory;
import io.prestosql.plugin.jdbc.ForBaseJdbc;
import io.prestosql.plugin.jdbc.JdbcClient;
import io.prestosql.plugin.jdbc.SessionPropertiesProvider;
import io.prestosql.plugin.jdbc.TypeHandlingJdbcConfig;
import io.prestosql.plugin.jdbc.credential.CredentialProvider;

import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static io.airlift.configuration.ConfigBinder.configBinder;

import java.util.Properties;

public class DB2ClientModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.bind(JdbcClient.class).annotatedWith(ForBaseJdbc.class).to(DB2Client.class).in(Scopes.SINGLETON);
        configBinder(binder).bindConfig(BaseJdbcConfig.class);
        configBinder(binder).bindConfig(TypeHandlingJdbcConfig.class);
        configBinder(binder).bindConfig(DecimalConfig.class);
        newSetBinder(binder, SessionPropertiesProvider.class).addBinding().to(DecimalSessionPropertiesProvider.class).in(Scopes.SINGLETON);
    }
    
    @Provides
    @Singleton
    @ForBaseJdbc
    public static ConnectionFactory getConnectionFactory(BaseJdbcConfig config, CredentialProvider credentialProvider)
    {
    	Properties connectionProperties = new Properties();
    	// https://www-01.ibm.com/support/knowledgecenter/ssw_ibm_i_72/rzaha/conprop.htm
        // block size (a.k.a fetch size), default 32
        connectionProperties.setProperty("block size", "512");
    	
    	return new DriverConnectionFactory(new DB2Driver(), config.getConnectionUrl(), connectionProperties, credentialProvider);
    }
}
