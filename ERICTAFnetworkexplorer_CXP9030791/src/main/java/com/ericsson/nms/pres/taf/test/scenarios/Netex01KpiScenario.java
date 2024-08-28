/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2020
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.nms.pres.taf.test.scenarios;

import com.ericsson.cifwk.taf.TafTestBase;
import com.ericsson.cifwk.taf.TafTestContext;
import com.ericsson.cifwk.taf.TestContext;
import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.cifwk.taf.configuration.TafConfigurationProvider;
import com.ericsson.cifwk.taf.datasource.TafDataSources;
import com.ericsson.cifwk.taf.scenario.TestScenario;
import com.ericsson.cifwk.taf.scenario.TestScenarioRunner;
import com.ericsson.cifwk.taf.scenario.TestScenarios;
import com.ericsson.cifwk.taf.scenario.api.TestStepFlowBuilder;
import com.ericsson.cifwk.taf.scenario.impl.LoggingScenarioListener;
import com.ericsson.cifwk.taf.scenario.impl.ScenarioExecutionContext;
import com.ericsson.cifwk.taf.scenario.impl.SyncSingleInvocation;
import com.ericsson.oss.testware.networkexplorer.constants.DataSources;
import com.ericsson.oss.testware.networkexplorer.constants.NetexKpiConstants;
import com.ericsson.oss.testware.networkexplorer.flows.NetexKpiReportGenerationFlow;
import com.ericsson.oss.testware.networkexplorer.flows.NetworkExplorerFlows;
import com.ericsson.oss.testware.security.authentication.flows.LoginLogoutRestFlows;
import com.ericsson.oss.testware.security.authentication.flows.LoginLogoutUiFlows;
import com.ericsson.oss.testware.security.gim.flows.GimCleanupFlows;
import com.ericsson.oss.testware.security.gim.flows.UserManagementTestFlows;
import com.ericsson.oss.testware.security.gim.steps.UserManagementTestSteps;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;


import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

import static com.ericsson.cifwk.taf.scenario.TestScenarios.*;
import static com.ericsson.oss.testware.enmbase.data.CommonDataSources.*;
import static com.ericsson.oss.testware.security.gim.flows.GimCleanupFlows.EnmObjectType;

/**
 * The responsibility of this class is to manage the test scenarios of NEX_01 KPI.
 */
public class Netex01KpiScenario extends TafTestBase {

    @Inject
    private LoginLogoutUiFlows loginLogoutUiFlows;
    @Inject
    private LoginLogoutRestFlows loginLogoutRestFlows;
    @Inject
    private NetworkExplorerFlows networkExplorerFlows;
    @Inject
    private NetexKpiReportGenerationFlow netexKpiReportGenerationFlow;
    @Inject
    private TestContext testContext;
    @Inject
    private UserManagementTestSteps userManagementTestSteps;
    @Inject
    private GimCleanupFlows idmCleanupFlows;
    @Inject
    private UserManagementTestFlows userManagementFlows;

    private final TestScenarioRunner runner = runner().withListener(new LoggingScenarioListener()).build();

    public static final String NRM_SIZE = TafConfigurationProvider.provide().getProperty("nrm.size", "60k", String.class);
    
    private final String netExKpiType = "netEx-" + NRM_SIZE;
    /**
     * Execute a TAF Scenario
     * @param scenario scenario to execute
     */
    private void start(final TestScenario scenario) {
        //Workaround for CIS-11177 & waiting for CIP-9307
        TafTestContext.getContext().removeDataSource(DataSources.COLUMN_RESULTS);
        //End Workaround
        runner.start(scenario);
    }

    @BeforeSuite(alwaysRun = true)
    private void beforeSuite() {
        start(scenario("Create STKPI User")
                .addFlow(createUser())
                .build());
    }

    @AfterSuite(alwaysRun = true)
    private void tearDown() {
        start(scenario(
                "Delete STKI User")
                .addFlow(deleteUser())
                .withDefaultVusers(1)
                .build());
    }

    /**
     * This scenario checks the time it took the Network Explorer to perform a network search to find 1,000
     * locked cells.
     */
    @Test(enabled = true)
    @TestId(id = "ST_STKPI_NETEX_01", title = "CM: NEX: Find all locked cells in the network")
    public void netexKpi01() {
        final TestScenario scenario = scenario("Check Network Search Time")
                .addFlow(stkpiCheckNetexSearchTime())
                .addFlow(netexKpiReportGenerationFlow.attachReport()
                    .withDataSources(dataSource("Netex_nrmNodeType"), dataSource("netex01KpiSearchQuery").withFilter("netExKpiType == '"+netExKpiType+"'")))
                .build();
        runner.start(scenario);
    }

    /**
     * This scenario will check time taken for Network Element Search
     */
    private TestStepFlowBuilder stkpiCheckNetexSearchTime() {
        return flow("Check time taken for Network Element Search")
                .addSubFlow(networkExplorerFlows.getNetex01KpiSearchTime());
    }

    public TestStepFlowBuilder createUser() {
        return flow("Create STKPI ENM user for netex")
                .beforeFlow((resetAndAddDataSource(USERS_TO_CREATE)))
                .addSubFlow(userManagementFlows.createUser());
    }

    private TestStepFlowBuilder deleteUser() {
        return flow("Delete STKPI ENM user for netex")
                .beforeFlow(resetAndAddDataSource(USER_TO_CLEAN_UP))
                .addSubFlow(idmCleanupFlows.cleanUp(EnmObjectType.USER));
    }

    private TestStepFlowBuilder resetUserAndLogin() {
        return flow("Reset Available Users and login into ENM")
                .beforeFlow(resetAndAddDataSource(AVAILABLE_USERS))
                .addSubFlow(loginLogoutUiFlows.login());
    }

    private TestStepFlowBuilder logoutUserAndPause() {
        return flow("Logout User and pause")
                .addSubFlow(loginLogoutUiFlows.logout())
                .addSubFlow(loginLogoutUiFlows.cleanUpFlow())
                .addSubFlow(networkExplorerFlows.clearSessionAttributes())
                .afterFlow(TestScenarios.pause(NetexKpiConstants.Netex_KPI_FLOW_REPEAT, TimeUnit.MINUTES));
    }

    private SyncSingleInvocation resetAndAddDataSource(final String dataSourceName) {
        return new SyncSingleInvocation("Reset " + dataSourceName) {
            protected void runOnce(final ScenarioExecutionContext scenarioExecutionContext) {
                scenarioExecutionContext.getDataSourceContext().getDataSources().remove(dataSourceName);
                testContext.addDataSource(dataSourceName, TafDataSources.shared(TafDataSources.fromCsv("data/superUser.csv")));
            }
        };
    }
}
