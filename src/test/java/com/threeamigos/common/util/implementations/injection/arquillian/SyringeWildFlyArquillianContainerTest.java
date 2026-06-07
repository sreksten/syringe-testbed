package com.threeamigos.common.util.implementations.injection.arquillian;

import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentHelper;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayDeque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SyringeWildFlyArquillianContainerTest {

    @Test
    void deployRetriesOnceAfterDuplicateAndThenSucceeds() throws Exception {
        ScriptedDeploymentHelper helper = new ScriptedDeploymentHelper();
        helper.failDeploy(duplicateDeploymentFailure());
        TestContainer container = prepareContainer(helper);

        ProtocolMetaData metadata = container.deploy(mockArchive("retry-test.war"));

        assertNotNull(metadata);
        assertEquals(2, helper.getDeployCalls());
        assertEquals(1, helper.getUndeployCalls());
        assertEquals(1, container.getRetryPauseCalls());
    }

    @Test
    void deployFailsAfterExhaustingDuplicateRecoveryAttempts() throws Exception {
        ScriptedDeploymentHelper helper = new ScriptedDeploymentHelper();
        helper.failDeploy(duplicateDeploymentFailure());
        helper.failDeploy(duplicateDeploymentFailure());
        helper.failDeploy(duplicateDeploymentFailure());
        helper.failDeploy(duplicateDeploymentFailure());
        TestContainer container = prepareContainer(helper);

        DeploymentException deploymentException = assertThrows(
                DeploymentException.class,
                () -> container.deploy(mockArchive("still-duplicate.war")));

        assertTrue(deploymentException.getMessage().contains("after 4 duplicate-recovery attempt(s)"));
        assertEquals(4, helper.getDeployCalls());
        assertEquals(4, helper.getUndeployCalls());
        assertEquals(3, container.getRetryPauseCalls());
    }

    @Test
    void deployPropagatesNonDuplicateFailureWithoutRetry() throws Exception {
        ScriptedDeploymentHelper helper = new ScriptedDeploymentHelper();
        RuntimeException unexpectedFailure = new RuntimeException("boom");
        helper.failDeploy(unexpectedFailure);
        TestContainer container = prepareContainer(helper);

        DeploymentException deploymentException = assertThrows(
                DeploymentException.class,
                () -> container.deploy(mockArchive("non-duplicate.war")));

        assertSame(unexpectedFailure, deploymentException.getCause());
        assertEquals(1, helper.getDeployCalls());
        assertEquals(0, helper.getUndeployCalls());
        assertEquals(0, container.getRetryPauseCalls());
    }

    @Test
    void detectsMissingDeploymentFailureMarkers() {
        RuntimeException missingFailure = new RuntimeException(
                "WFLYCTL0216: Management resource '[(\"deployment\" => \"foo.war\")]' not found");

        assertTrue(SyringeWildFlyArquillianContainer.isMissingDeploymentFailure(missingFailure));
    }

    @SuppressWarnings("unchecked")
    private static Archive<?> mockArchive(String runtimeName) {
        Archive<?> archive = mock(Archive.class);
        ZipExporter zipExporter = mock(ZipExporter.class);
        when(archive.getName()).thenReturn(runtimeName);
        when(archive.as(ZipExporter.class)).thenReturn(zipExporter);
        return archive;
    }

    private static TestContainer prepareContainer(ScriptedDeploymentHelper helper) throws Exception {
        TestContainer container = new TestContainer(helper);

        SyringeWildFlyConfiguration configuration = new SyringeWildFlyConfiguration();
        configuration.setManagementHost("localhost");
        configuration.setHttpPort(8080);

        setField(container, "configuration", configuration);
        setField(container, "client", mock(ModelControllerClient.class));
        return container;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = SyringeWildFlyArquillianContainer.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static RuntimeException duplicateDeploymentFailure() {
        return new RuntimeException(
                "WFLYCTL0212: Duplicate resource [(\"deployment\" => \"duplicate.war\")]");
    }

    private static final class TestContainer extends SyringeWildFlyArquillianContainer {
        private final ServerDeploymentHelper helper;
        private int retryPauseCalls;

        private TestContainer(ServerDeploymentHelper helper) {
            this.helper = helper;
        }

        @Override
        ServerDeploymentHelper createDeploymentHelper() {
            return helper;
        }

        @Override
        void pauseBeforeDuplicateRetry(int attempt) {
            retryPauseCalls++;
        }

        int getRetryPauseCalls() {
            return retryPauseCalls;
        }
    }

    private static final class ScriptedDeploymentHelper extends ServerDeploymentHelper {
        private final ArrayDeque<RuntimeException> deployFailures = new ArrayDeque<RuntimeException>();
        private int deployCalls;
        private int undeployCalls;

        private ScriptedDeploymentHelper() {
            super(mock(ModelControllerClient.class));
        }

        void failDeploy(RuntimeException failure) {
            deployFailures.addLast(failure);
        }

        int getDeployCalls() {
            return deployCalls;
        }

        int getUndeployCalls() {
            return undeployCalls;
        }

        @Override
        public String deploy(String runtimeName, InputStream inputStream) {
            deployCalls++;
            if (!deployFailures.isEmpty()) {
                throw deployFailures.removeFirst();
            }
            return runtimeName;
        }

        @Override
        public void undeploy(String runtimeName) {
            undeployCalls++;
        }
    }
}
