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

import static com.ericsson.cifwk.taf.scenario.TestScenarios.dataSource;
import static com.ericsson.cifwk.taf.scenario.TestScenarios.flow;
import static com.ericsson.cifwk.taf.scenario.TestScenarios.runner;
import static com.ericsson.cifwk.taf.scenario.TestScenarios.scenario;
import static com.ericsson.oss.testware.networkexplorer.constants.DataSourceFields.EXPECTED_ROWS;
import static com.ericsson.oss.testware.networkexplorer.constants.DataSourceFields.EXPECTED_TABLE;
import static com.ericsson.oss.testware.networkexplorer.constants.DataSourceFields.RESULTS;
import static com.ericsson.oss.testware.security.gim.flows.GimCleanupFlows.EnmObjectType;

import javax.inject.Inject;

import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import com.ericsson.cifwk.taf.TafTestBase;
import com.ericsson.cifwk.taf.TafTestContext;
import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.cifwk.taf.data.DataHandler;
import com.ericsson.cifwk.taf.scenario.TestScenario;
import com.ericsson.cifwk.taf.scenario.TestScenarioRunner;
import com.ericsson.cifwk.taf.scenario.api.ScenarioExceptionHandler;
import com.ericsson.cifwk.taf.scenario.impl.LoggingScenarioListener;
import com.ericsson.oss.testware.enmbase.data.CommonDataSources;
import com.ericsson.oss.testware.networkexplorer.constants.DataSourceFields;
import com.ericsson.oss.testware.networkexplorer.constants.DataSources;
import com.ericsson.oss.testware.networkexplorer.flows.CollectionsFlows;
import com.ericsson.oss.testware.networkexplorer.flows.CreateCollectionFlows;
import com.ericsson.oss.testware.networkexplorer.flows.EditExistingCollectionFlows;
import com.ericsson.oss.testware.networkexplorer.flows.NetworkExplorerFlows;
import com.ericsson.oss.testware.nodeintegration.flows.NodeIntegrationFlows;
import com.ericsson.oss.testware.security.authentication.flows.LoginLogoutRestFlows;
import com.ericsson.oss.testware.security.authentication.flows.LoginLogoutUiFlows;
import com.ericsson.oss.testware.security.gim.flows.GimCleanupFlows;
import com.ericsson.oss.testware.security.gim.flows.UserManagementTestFlows;

/**
 * A set of tests verifying the core features that Network Explorer provides
 */
public class CoreScenarios extends TafTestBase {

    /**
     * Property for default user to login e.g. mvn clean install -Ddefault.user.name=networkexploreruser01
     */
    private static final String DEFAULT_USER_PROPERTY = "default.user.name";
    public static final String DEFAULT_USER = DataHandler.getConfiguration().getProperty(
            DEFAULT_USER_PROPERTY,
            "NetworkExplorer_ADMIN",
            String.class);

    /**
     * Property for alternate user to login e.g. mvn clean install -Dalternate.user.name=networkexploreruser02
     * Alternate user is a read only user and does not create any persistent data
     */
    private static final String ALTERNATE_USER_PROPERTY = "alternate.user.name";
    public static final String ALTERNATE_USER = DataHandler.getConfiguration().getProperty(
            ALTERNATE_USER_PROPERTY,
            "NetworkExplorer_OPERATOR",
            String.class);

    @Inject
    private GimCleanupFlows idmCleanupFlows;

    @Inject
    private UserManagementTestFlows userManagementFlows;

    @Inject
    private LoginLogoutRestFlows loginLogoutRestFlows;

    @Inject
    private LoginLogoutUiFlows loginLogoutUiFlows;

    @Inject
    private NodeIntegrationFlows nodeIntegrationFlows;

    @Inject
    private CreateCollectionFlows createCollectionFlows;

    @Inject
    private EditExistingCollectionFlows editExistingCollectionFlows;

    @Inject
    private NetworkExplorerFlows networkExplorerFlows;

    @Inject
    private CollectionsFlows collectionsFlows;

    private static TestScenarioRunner runner = runner().withListener(
            new LoggingScenarioListener()).build();

    /**
     * Execute a TAF Scenario
     * @param scenario scenario to execute
     */
    private static void start(final TestScenario scenario) {
        //Workaround for CIS-11177 & waiting for CIP-9307
        TafTestContext.getContext().removeDataSource(DataSources.COLUMN_RESULTS);
        //End Workaround
        runner.start(scenario);
    }

    @BeforeSuite(alwaysRun = true)
    private void beforeSuite() {
        CommonDataSources.initializeDataSources();
        start(scenario("Create users and cleanup any existing user data then add/sync nodes")
                .addFlow(idmCleanupFlows.cleanUp(EnmObjectType.USER))
                .addFlow(userManagementFlows.createUser())
                .addFlow(loginLogoutRestFlows.loginWithUserName(DEFAULT_USER))
                .addFlow(networkExplorerFlows.populateBrowser())
                .addFlow(collectionsFlows.deleteMyCollectionsAndSavedSearches())
                .addFlow(networkExplorerFlows.netexModelServiceWarmup())
                .addFlow(loginLogoutRestFlows.logout())
                .addFlow(networkExplorerFlows.clearSessionAttributes())
                .addFlow(loginLogoutRestFlows.loginDefaultUser())
                .addFlow(flow("Node agnostic add & sync node")
                        .addSubFlow(nodeIntegrationFlows.addNode())
                        .addSubFlow(nodeIntegrationFlows.syncNode())
                        .withDataSources(dataSource(CommonDataSources.NODES_TO_ADD))
                        .build())
                .addFlow(loginLogoutRestFlows.logout())
                .withDefaultVusers(1)
                .build());
    }

    @AfterSuite(alwaysRun = true)
    private void tearDown() {
        final TestScenario persistentDataCleanupScenario = scenario(
                "Clean up any Collections and Saved Searches created by default user")
                .addFlow(loginLogoutRestFlows.loginWithUserName(DEFAULT_USER))
                .addFlow(networkExplorerFlows.populateBrowser())
                .addFlow(collectionsFlows.deleteMyCollectionsAndSavedSearches())
                .addFlow(loginLogoutRestFlows.logout())
                .withExceptionHandler(ScenarioExceptionHandler.LOGONLY)
                .withDefaultVusers(1)
                .build();
        final TestScenario nodeTearDownScenario = scenario(
                "Delete nodes")
                .addFlow(loginLogoutRestFlows.loginDefaultUser())
                .addFlow(flow("Node agnostic delete node")
                        .addSubFlow(nodeIntegrationFlows.deleteNode())
                        .withDataSources(dataSource(CommonDataSources.ADDED_NODES))
                        .build())
                .addFlow(loginLogoutRestFlows.logout())
                .withExceptionHandler(ScenarioExceptionHandler.LOGONLY)
                .withDefaultVusers(1)
                .build();
        final TestScenario userTearDownScenario = scenario(
                "Delete users")
                .addFlow(idmCleanupFlows.cleanUp(EnmObjectType.USER))
                .withDefaultVusers(1)
                .build();
        try {
            start(nodeTearDownScenario);
        } finally {
            try {
                start(persistentDataCleanupScenario);
            } finally {
                start(userTearDownScenario);
            }
        }
    }

    /**
     SCENARIO 1 - EXECUTE A SIMPLE SEARCH

    Create users
    Add/Sync nodes

    Login
    Launch Network Explorer
    Execute Search on Network Element where name = 'LTE10ERBS00159'
    Verify results
    Check if element exists in table
    Logout

    Delete users
    Delete nodes
    */
    @Test(enabled = true)
    @TestId(id = "NETWORK_EXPLORER_SCENARIO_1", title = "Execute a simple search")
    public void executeASimpleSearch() {
        final TestScenario scenario = scenario(
                "Execute a simple search")
                .addFlow(loginLogoutUiFlows.loginWithUserName(DEFAULT_USER))
                .addFlow(networkExplorerFlows.populateBrowser())
                .addFlow(networkExplorerFlows.openNetworkExplorer())
                .addFlow(networkExplorerFlows.executeSimpleSearch())
                .addFlow(networkExplorerFlows.checkNetworkElementIdExistsInNameColumn())
                .addFlow(loginLogoutUiFlows.logout())
                .addFlow(loginLogoutUiFlows.cleanUpFlow())
                .addFlow(networkExplorerFlows.clearSessionAttributes())
                .build();
        start(scenario);
    }

    /**
     SCENARIO 2 - CRITERIA BUILDER NODE SEARCH (with name)

     Create users
     Add/Sync nodes

     Login
     Launch Network Explorer
     Switch to criteria builder
     Select nodes(s)
     Select node type
     Enter name generated from syncing nodes
     Execute Search
     Verify results
        MoType
        NeType
        name
     Logout

     Delete users
     Delete nodes
     */
    @Test(enabled = true)
    @TestId(id = "Q2_Functional_NETWORK_EXPLORER_SCENARIO_3", title = "Criteria Builder Node search with optional name")
    public void criteriaBuilderNodeSearchWithName() {
        final TestScenario scenario = scenario(
                "Criteria Builder Node search with optional name")
                .addFlow(loginLogoutUiFlows.loginWithUserName(DEFAULT_USER))
                .addFlow(networkExplorerFlows.populateBrowser())
                .addFlow(networkExplorerFlows.openNetworkExplorer())
                .addFlow(networkExplorerFlows.switchToCriteriaBuilder())
                .addFlow(networkExplorerFlows.doNodeSearchWithName())
                .addFlow(networkExplorerFlows.checkNodeSearchWithNameValuesMatchColumns())
                .addFlow(loginLogoutUiFlows.logout())
                .addFlow(loginLogoutUiFlows.cleanUpFlow())
                .addFlow(networkExplorerFlows.clearSessionAttributes())
                .build();
        start(scenario);
    }

    /**
     SCENARIO 3 - CRITERIA BUILDER MO SEARCH (EUtranCellFDD and EUtranFreqRelation)

     Create users
     Add/Sync nodes

     Login
     Launch Network Explorer
     Switch to criteria builder
     Select MO type
     Chose Model (ERBS)
     Choose Managed Object (EUtranCellFDD)
     Select Has Child
     Choose EUtranFreqRelation
     Execute Search
     Verify Results
        MoType column shows EUtranCellFDD and EUtranFreqRelation
     Logout

     Delete users
     Delete nodes
     */
    @Test(enabled = true)
    @TestId(id = "Q2_Functional_NETWORK_EXPLORER_SCENARIO_4", title = "Criteria Builder Managed Object search")
    public void criteriaBuilderMoSearchForCellsAndRelations() {
        final TestScenario scenario = scenario(
                "Criteria Builder Managed Object search")
                .addFlow(loginLogoutUiFlows.loginWithUserName(DEFAULT_USER))
                .addFlow(networkExplorerFlows.populateBrowser())
                .addFlow(networkExplorerFlows.openNetworkExplorer())
                .addFlow(networkExplorerFlows.switchToCriteriaBuilder())
                .addFlow(networkExplorerFlows.populateQueryBuilderWithMeContextAndNode())
                .addFlow(networkExplorerFlows.populateQueryBuilderWithCellsAndRelations())
                .addFlow(networkExplorerFlows.hideMeContextAfterEyeIconAppears())
                .addFlow(networkExplorerFlows.performQueryBuilderSearch())
                .addFlow(networkExplorerFlows.getResultsFromResultsTable())
                .addFlow(networkExplorerFlows.verifyManagedObjectsAppearInResults())
                .addFlow(loginLogoutUiFlows.logout())
                .addFlow(loginLogoutUiFlows.cleanUpFlow())
                .addFlow(networkExplorerFlows.clearSessionAttributes())
                .build();
        start(scenario);
    }

    /**
     SCENARIO 4 - CREATE A COLLECTION

     Create users
     Add/Sync nodes

     Login
     1 Launch Network Explorer
     2 In search box search for EUtranCellRelation
        Execute Search
     3 Select All checkbox
        Click Add objects to a Collection
     4 Enter collection name (Default private)
     5 Save
     6 Select collection on Collection Management page
        6.1 Verify Compare what was added is what is returned in results
     7 Select the star icon to favorite the collection
        7.1 Verify the favorited icon is highlighted

     ADD OBJECTS TO EXISTING COLLECTION

     8 In search box search for MeContext
        Execute Search
     9 Select All checkbox
        Click Add Objects to a collection
     10 Select Add to an existing Collection
     11 Enter Collection name into Collection Selector filter box
     12 Select Collection from Collection Selector table
     13 Click Add button
        13.1 Verify Collection remains under Collections on left
        13.2 Verify Check that collection contains new items added

     OVERWRITE EXISTING COLLECTION

     14 In search box search for NetworkElement
        Execute Search
     15 Select All checkbox
        Click Add Objects to a collection
     16 Select Add to an existing Collection
     17 Check replace checkbox to overwrite existing collection contents
     18 Enter Collection name into Collection Selector filter box
     19 Select Collection from Collection Selector table
     20 Click Add button
        20.1 Verify Collection remains under Collections on left
        20.2 Verify Check that collection contains new items added
     21 Logout

     REMOVE OBJECTS FROM EXISTING COLLECTION

     22 Login
     23 Launch Network Explorer
     24 Load collection from side bar
     25 In search box search for MeContext
        Execute search
     26  Select All checkbox
        Click Add to a collection
     27 Select Add to an existing Collection
     28 Enter Collection name into Collection Selector filter box
     29 Select Collection from Collection Selector table
     30 Click Add button
        30.1 Verify Check that collection contains new items added
     31 Remove NetworkElement objects from collection
     32 Verify that collection only contains MeContext results

     SCENARIO 5 - VIEW AND DELETE A COLLECTION

     33 Click on View All link
        35.1 Verify Name of collection appears in table
     34 Click the bin icon
        34.1 Select Delete Collection
        34.2 Verify you have no collections
     35 Verify the collection disappears in the Collections &amp; Searches list under Collections
     36 Logout

     Delete users
     Delete nodes
    */
    @Test(groups = { "RFA250" }, enabled = true)
    @TestId(id = "Q2_Functional_NETWORK_EXPLORER_SCENARIO_5", title = "Create a Collection, then append new objects, then overwrite objects, then View and delete a collection")
    public void createACollection() {
        final TestScenario scenario = scenario(
                "Create a Collection, then append new objects, then overwrite objects, then View and delete a collection")
                .addFlow(loginLogoutUiFlows.loginWithUserName(DEFAULT_USER))
                .addFlow(networkExplorerFlows.populateBrowser())
                .addFlow(networkExplorerFlows.openNetworkExplorer())
                .addFlow(networkExplorerFlows.searchForEUtranCellRelations())
                .addFlow(networkExplorerFlows.selectAllObjects())
                .addFlow(createCollectionFlows.createCollectionOfEUtranCellRelations()) //from our search. Keep a copy of results returned.
                .addFlow(networkExplorerFlows.getResultsForColumns())
                .addFlow(networkExplorerFlows.viewContentsOfEUtranCellRelationCollection())
                .addFlow(networkExplorerFlows.verifyCollectionOfEUtranCellRelationsMatchesInitialSearch()) //verify our results match the results returned
                .addFlow(networkExplorerFlows.searchForMeContext())
                .addFlow(networkExplorerFlows.selectAllObjects())
                .addFlow(editExistingCollectionFlows.addMeContextToExistingEUtranCellRelationCollection())
                .addFlow(networkExplorerFlows.getResultsForColumnsBuilder()
                        .withDataSources(
                                dataSource(DataSources.ALL_HEADERS))
                        .afterFlow(
                                networkExplorerFlows.copyDataSource(DataSources.COLUMN_RESULTS, DataSources.RECORDED_RESULTS))
                        .build())
                .addFlow(networkExplorerFlows.viewContentsOfEUtranCellRelationCollection())
                .addFlow(networkExplorerFlows.verifyDatasourceContainsResults()
                        .withDataSources(
                            dataSource(DataSources.RECORDED_RESULTS)
                                    .bindColumn(RESULTS, EXPECTED_ROWS),
                            dataSource(DataSources.COLUMN_RESULTS))
                        .build())
                .addFlow(networkExplorerFlows.searchForNetworkElement())
                .addFlow(networkExplorerFlows.selectAllObjects())
                .addFlow(editExistingCollectionFlows.overwriteExistingCollectionWithNetworkElement())
                .addFlow(networkExplorerFlows.getResultsForColumnsBuilder()
                        .withDataSources(
                                dataSource(DataSources.ALL_HEADERS))
                        .afterFlow(
                                networkExplorerFlows.copyDataSource(DataSources.COLUMN_RESULTS, DataSources.RECORDED_RESULTS))
                        .build())
                .addFlow(networkExplorerFlows.viewContentsOfEUtranCellRelationCollection())
                .addFlow(networkExplorerFlows.verifyDatasourceContainsResults()
                        .withDataSources(
                                dataSource(DataSources.RECORDED_RESULTS)
                                        .bindColumn(RESULTS, EXPECTED_ROWS),
                                dataSource(DataSources.COLUMN_RESULTS))
                        .afterFlow(
                                networkExplorerFlows.deleteDataSource(DataSources.RECORDED_RESULTS))
                        .build())
                .addFlow(loginLogoutUiFlows.logout())
                .addFlow(loginLogoutUiFlows.cleanUpFlow())
                .addFlow(networkExplorerFlows.clearSessionAttributes())
                .addFlow(loginLogoutUiFlows.loginWithUserName(DEFAULT_USER))
                .addFlow(networkExplorerFlows.populateBrowser())
                .addFlow(networkExplorerFlows.viewContentsOfExistingCollection()
                        .withDataSources(
                                dataSource(DataSources.COLLECTION_OF_EU_TRAN_CELL_RELATIONS))
                        .afterFlow(networkExplorerFlows.deleteDataSource(DataSources.COLUMN_RESULTS))
                        .build())
                .addFlow(networkExplorerFlows.searchForMeContext())
                .addFlow(networkExplorerFlows.getResultsForColumnsBuilder()
                        .withDataSources(
                                dataSource(DataSources.ALL_HEADERS))
                        .afterFlow(networkExplorerFlows.copyDataSource(DataSources.COLUMN_RESULTS, DataSources.RECORDED_RESULTS))
                        .build())
                .addFlow(networkExplorerFlows.selectAllObjects())
                .addFlow(editExistingCollectionFlows.addMeContextToExistingEUtranCellRelationCollection())
                .addFlow(networkExplorerFlows.viewContentsOfExistingCollection()
                        .addSubFlow(networkExplorerFlows.removeObjectsFromCollection())
                        .withDataSources(
                                dataSource(DataSources.NETWORK_ELEMENT_SEARCH_BY_NAME),
                                dataSource(CommonDataSources.SYNCED_NODES)
                                        .bindColumn(DataSourceFields.NETWORK_ELEMENT_ID, DataSourceFields.OBJECT_NAME),
                                dataSource(DataSources.COLLECTION_OF_NETWORK_ELEMENT))
                        .build())
                .addFlow(networkExplorerFlows.getResultsForColumnsBuilder()
                        .withDataSources(
                                dataSource(DataSources.ALL_HEADERS))
                        .build())
                .addFlow(networkExplorerFlows.verifyExpectedResultsMatchActualResults()
                        .withDataSources(
                                dataSource(DataSources.RECORDED_RESULTS)
                                        .bindColumn(RESULTS, EXPECTED_TABLE),
                                dataSource(DataSources.COLUMN_RESULTS))
                        .afterFlow(
                                networkExplorerFlows.deleteDataSource(DataSources.RECORDED_RESULTS))
                        .build())
                .addFlow(networkExplorerFlows.viewCollectionInAllCollections())
                .addFlow(collectionsFlows.deleteCollectionInAllCollectionsUsingDeleteButton())
                .addFlow(loginLogoutUiFlows.logout())
                .addFlow(loginLogoutUiFlows.cleanUpFlow())
                .addFlow(networkExplorerFlows.clearSessionAttributes())
                .build();
        start(scenario);
    }

    /**
     SCENARIO 6 - CREATE A SAVED SEARCH

     Create users
     Add/Sync nodes

     Login
     Launch Network Explorer
     in search box search for ENodeB
     Execute Search
     Select Save Search button
     Enter saved search name (Select public)
     Save
     Verify
     Logout
     Login alternate user
     The saved search is visible on left
     Click saved search on left
     Verify
        Compare what was added is what is returned in results
     Select the star icon to favourite the saved search
     Verify
        the favorited saved search appears in the list under Saved Searches
     Logout alternate user

     SCENARIO 7 - VIEW AND DELETE A SAVED SEARCH

     Login
     Click on View All link
     Verify
     Name of saved search appears in table
     Click the bin icon
     Select Delete saved search
     Verify
     You have no saved searches
     Verify
     the saved search disappears in the Collections &amp; Searches list under Saved Searches
     Logout

     Delete users
     Delete nodes
    */
    @Test(groups = { "RFA250" }, enabled = true)
    @TestId(id = "Q2_Functional_NETWORK_EXPLORER_SCENARIO_7_7668", title = "Create a Saved Search then View and delete a Saved Search")
    public void createASavedSearch() {
        final TestScenario scenario = scenario(
                "Create a Saved Search then View and delete a Saved Search")
                .addFlow(loginLogoutUiFlows.loginWithUserName(DEFAULT_USER))
                .addFlow(networkExplorerFlows.populateBrowser())
                .addFlow(networkExplorerFlows.openNetworkExplorer())
                .addFlow(networkExplorerFlows.searchForENodeBFunctions())
                .addFlow(networkExplorerFlows.createSavedSearchForENodeBFunctions()) //with our data. Keep a copy of our data.
                .addFlow(loginLogoutUiFlows.logout()) //switch user to verify public
                .addFlow(loginLogoutUiFlows.cleanUpFlow())
                .addFlow(networkExplorerFlows.clearSessionAttributes())
                .addFlow(loginLogoutUiFlows.loginWithUserName(ALTERNATE_USER))
                .addFlow(networkExplorerFlows.populateBrowser())
                .addFlow(networkExplorerFlows.openNetworkExplorer())
                .addFlow(networkExplorerFlows.verifySavedSearchForENodeBFunctionsInSavedSearchManagementPage())
                .addFlow(networkExplorerFlows.verifySavedSearchForENodeBFunctionsMatchesInitialSearch()) //verify our data matches data returned from service
                .addFlow(loginLogoutUiFlows.logout())
                .addFlow(loginLogoutUiFlows.cleanUpFlow())
                .addFlow(networkExplorerFlows.clearSessionAttributes())
                .addFlow(loginLogoutUiFlows.loginWithUserName(DEFAULT_USER))
                .addFlow(networkExplorerFlows.populateBrowser())
                .addFlow(networkExplorerFlows.openNetworkExplorer())
                .addFlow(networkExplorerFlows.viewSavedSearchAndCheckFavoriteUnfavoriteWorksOnSavedSearches())
                .addFlow(collectionsFlows.deleteSavedSearchInAllSavedSearchesUsingDeleteButton())
                .addFlow(loginLogoutUiFlows.logout())
                .addFlow(loginLogoutUiFlows.cleanUpFlow())
                .addFlow(networkExplorerFlows.clearSessionAttributes())
                .build();
        start(scenario);
    }

}
