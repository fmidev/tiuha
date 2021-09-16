package fi.fmi.tiuha.geomesa;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFactorySpi;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

import scala.Option;
import scala.Tuple3;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.QueryOptions;
import com.datastax.driver.core.Session;
import org.locationtech.geomesa.cassandra.data.CassandraDataStoreFactory;
import org.locationtech.geomesa.utils.audit.AuditLogger$;
import org.locationtech.geomesa.utils.audit.AuditProvider;
import org.locationtech.geomesa.utils.audit.AuditWriter;
import org.locationtech.geomesa.utils.audit.NoOpAuditProvider$;
import org.locationtech.geomesa.cassandra.data.CassandraDataStore;
import software.aws.mcs.auth.SigV4AuthProvider;

public class KeyspacesDataStoreFactory implements DataStoreFactorySpi {

    private static Param regionParam = new Param("keyspaces.region", String.class, "AWS region for the keyspace", true);
    private static Param keyspaceParam = new Param("keyspaces.keyspaceName", String.class, "Name of keyspace to connect to", true);
    private static Param catalogParam = new Param("keyspaces.catalog", String.class, "Name of catalog table to use", true);

    public String getDisplayName() {
        return "Keyspaces (Geomesa)";
    }

    public String getDescription() {
        return "AWS Keyspaces data store";
    }

    public Param[] getParametersInfo() {
        return new Param[]{regionParam, keyspaceParam, catalogParam};
    }

    public boolean isAvailable() {
        return true;
    }

    public DataStore createDataStore(Map<String, Serializable> params) throws IOException {
        String region = (String) regionParam.lookUp(params);
        String keyspaceName = (String) keyspaceParam.lookUp(params);
        String catalog = (String) catalogParam.lookUp(params);

        QueryOptions queryOptions = new QueryOptions();
        queryOptions.setConsistencyLevel(ConsistencyLevel.LOCAL_QUORUM);

        Cluster cluster = Cluster.builder()
                .addContactPoint("cassandra." + region + ".amazonaws.com")
                .withPort(9142)
                .withAuthProvider(new SigV4AuthProvider(region))
                .withSSL()
                .withQueryOptions(queryOptions)
                .build();
        Session session = cluster.connect(keyspaceName);

        boolean generateStats = true;
        int threads = 4;
        boolean looseBBox = false;
        boolean caching = false;

        Tuple3<AuditWriter, AuditProvider, String> audit = new Tuple3<AuditWriter, AuditProvider, String>(
            AuditLogger$.MODULE$, NoOpAuditProvider$.MODULE$, "Keyspaces"
        );

        CassandraDataStoreFactory.CassandraQueryConfig queryConfig = new CassandraDataStoreFactory.CassandraQueryConfig(
            threads,
            Option.empty(),
            looseBBox,
            caching
        );

        CassandraDataStoreFactory.CassandraDataStoreConfig config = new CassandraDataStoreFactory.CassandraDataStoreConfig(
                catalog,
                generateStats,
                Option.apply(audit),
                queryConfig,
                Option.<String>empty()
        );
        return new CassandraDataStore(session, config);
    }

    public DataStore createNewDataStore(Map<String, Serializable> params) throws IOException {
        return createDataStore(params);
    }

}