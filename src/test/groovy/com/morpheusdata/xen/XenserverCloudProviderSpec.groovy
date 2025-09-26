package com.morpheusdata.xen

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.backup.BackupProvider
import com.morpheusdata.core.providers.ProvisionProvider
import com.morpheusdata.model.*
import com.morpheusdata.request.ValidateCloudRequest
import com.morpheusdata.response.ServiceResponse
import spock.lang.Specification
import com.morpheusdata.xen.util.XenComputeUtility
import com.morpheusdata.core.util.ConnectionUtils
import com.morpheusdata.xen.sync.HostSync
import com.morpheusdata.xen.sync.ImagesSync
import com.morpheusdata.xen.sync.NetworkSync
import com.morpheusdata.xen.sync.DatastoresSync
import com.morpheusdata.xen.sync.PoolSync
import com.morpheusdata.xen.sync.VirtualMachineSync

class XenserverCloudProviderSpec extends Specification {
    def plugin = Mock(XenserverPlugin)
    def context = Mock(MorpheusContext)
    def provider

    def setup() {
        provider = new XenserverCloudProvider(plugin, context)
    }

    def cleanup() {
        // Clean up any metaClass modifications to avoid test interference
        GroovySystem.metaClassRegistry.removeMetaClass(XenserverProvisionProvider)
        GroovySystem.metaClassRegistry.removeMetaClass(XenComputeUtility)
        GroovySystem.metaClassRegistry.removeMetaClass(ConnectionUtils)
    }

    def "constructor assigns plugin and context"() {
        expect:
        provider.plugin == plugin
        provider.context == context
    }

    def "getDescription returns XCP-ng"() {
        expect:
        provider.getDescription() == 'XCP-ng'
    }

    def "getIcon returns correct icon"() {
        when:
        def icon = provider.getIcon()
        then:
        icon.path == 'xcpng-light.svg'
        icon.darkPath == 'xcpng-dark.svg'
    }

    def "getCircularIcon returns correct circular icon"() {
        when:
        def icon = provider.getCircularIcon()
        then:
        icon.path == 'xcpng-circular-light.svg'
        icon.darkPath == 'xcpng-circular-dark.svg'
    }

    def "getOptionTypes returns expected option types"() {
        when:
        def options = provider.getOptionTypes()
        then:
        options*.fieldName.containsAll(['apiUrl','apiPort','type','username','password','importExisting','enableVnc'])
    }

    def "getAvailableProvisionProviders returns plugin provision providers"() {
        given:
        def mockProviders = [Mock(ProvisionProvider)]
        plugin.getProvidersByType(ProvisionProvider) >> mockProviders
        expect:
        provider.getAvailableProvisionProviders() == mockProviders
    }

    def "getAvailableBackupProviders returns plugin backup providers"() {
        given:
        def mockProviders = [Mock(BackupProvider)]
        plugin.getProvidersByType(BackupProvider) >> mockProviders
        expect:
        provider.getAvailableBackupProviders() == mockProviders
    }

    def "getNetworkTypes returns network types including xenNetwork"() {
        given:
        // Create a proper expando object that supports all needed methods
        def networkService = new Expando()
        networkService.list = { query -> [] }

        def services = new Expando()
        services.network = networkService

        context.metaClass.getServices = { -> services }

        when:
        def types = provider.getNetworkTypes()
        then:
        types.find { it.code == 'xenNetwork' }
    }

    def "getSubnetTypes returns empty list"() {
        expect:
        provider.getSubnetTypes().isEmpty()
    }

    def "getStorageVolumeTypes returns empty list"() {
        expect:
        provider.getStorageVolumeTypes().isEmpty()
    }

    def "getStorageControllerTypes returns empty list"() {
        expect:
        provider.getStorageControllerTypes().isEmpty()
    }

    def "getComputeServerTypes returns expected server types"() {
        when:
        def types = provider.getComputeServerTypes()
        then:
        types*.code.containsAll(['xenserverHypervisor','xenserverMetalHypervisor','xenserverLinux','xenKubeMaster','xenKubeWorker','xenserverVm','xenserverWindowsVm','xenserverUnmanaged'])
    }

    def "validate returns success"() {
        expect:
        provider.validate(Mock(Cloud), Mock(ValidateCloudRequest)).success
    }

    def "initializeCloud returns success when host is online"() {
        given:
        def cloud = Mock(Cloud) {
            getApiProxy() >> null
            getConfigMap() >> [:]
        }

        // Mock static utility method
        XenComputeUtility.metaClass.static.getXenApiUrl = { Cloud c -> 'http://localhost' }
        ConnectionUtils.metaClass.static.testHostConnectivity = { String host, int port, boolean a, boolean b, NetworkProxy proxy -> true }

        // Mock refresh method
        def testProvider = Spy(XenserverCloudProvider, constructorArgs: [plugin, context])
        testProvider.refresh(_) >> ServiceResponse.success()

        when:
        def resp = testProvider.initializeCloud(cloud)

        then:
        resp.success
    }

    def "refresh returns success when testConnection is successful"() {
        given:
        def cloud = Mock(Cloud)

        // Mock static method
        XenComputeUtility.metaClass.static.testConnection = { Map config -> [success: true] }

        // Mock sync classes
        HostSync.metaClass.constructor = { Cloud c, XenserverPlugin p ->
            def mock = Mock(HostSync)
            mock.execute() >> null
            mock
        }
        ImagesSync.metaClass.constructor = { Cloud c, XenserverPlugin p ->
            def mock = Mock(ImagesSync)
            mock.execute() >> null
            mock
        }
        NetworkSync.metaClass.constructor = { Cloud c, XenserverPlugin p ->
            def mock = Mock(NetworkSync)
            mock.execute() >> null
            mock
        }
        DatastoresSync.metaClass.constructor = { Cloud c, XenserverPlugin p ->
            def mock = Mock(DatastoresSync)
            mock.execute() >> null
            mock
        }
        PoolSync.metaClass.constructor = { Cloud c, XenserverPlugin p ->
            def mock = Mock(PoolSync)
            mock.execute() >> null
            mock
        }
        VirtualMachineSync.metaClass.constructor = { Cloud c, XenserverPlugin p, XenserverCloudProvider pr ->
            def mock = Mock(VirtualMachineSync)
            mock.execute() >> null
            mock
        }

        when:
        def resp = provider.refresh(cloud)

        then:
        resp.success
    }

    def "refreshDaily sets cloud status and saves"() {
        given:
        def cloud = Mock(Cloud)

        // Mock static method
        XenComputeUtility.metaClass.static.testConnection = { Map config -> [success: true] }

        // Create proper expando objects that support all needed methods
        def subscribeMock = new Expando()
        subscribeMock.dispose = { -> null }

        def saveMock = new Expando()
        saveMock.subscribe = { -> subscribeMock }

        def cloudAsync = new Expando()
        cloudAsync.save = { c -> saveMock }

        def asyncServices = new Expando()
        asyncServices.cloud = cloudAsync

        context.metaClass.getAsync = { -> asyncServices }

        when:
        provider.refreshDaily(cloud)

        then:
        notThrown(Exception)
    }

    def "deleteCloud returns success"() {
        expect:
        provider.deleteCloud(Mock(Cloud)).success
    }

    def "hasComputeZonePools returns false"() {
        expect:
        !provider.hasComputeZonePools()
    }

    def "provisionRequiresResourcePool returns false"() {
        expect:
        !provider.provisionRequiresResourcePool()
    }

    def "hasNetworks returns true"() {
        expect:
        provider.hasNetworks()
    }

    def "hasFolders returns false"() {
        expect:
        !provider.hasFolders()
    }

    def "hasDatastores returns true"() {
        expect:
        provider.hasDatastores()
    }

    def "hasBareMetal returns false"() {
        expect:
        !provider.hasBareMetal()
    }

    def "hasCloudInit returns true"() {
        expect:
        provider.hasCloudInit()
    }

    def "supportsDistributedWorker returns true"() {
        expect:
        provider.supportsDistributedWorker()
    }

    def "startServer delegates to provisionProvider"() {
        given:
        def computeServer = Mock(ComputeServer)
        XenserverProvisionProvider.metaClass.constructor = { XenserverPlugin p, MorpheusContext c ->
            Mock(XenserverProvisionProvider) {
                startServer(_) >> ServiceResponse.success()
            }
        }
        expect:
        provider.startServer(computeServer).success
    }

    def "stopServer delegates to provisionProvider"() {
        given:
        def computeServer = Mock(ComputeServer)
        XenserverProvisionProvider.metaClass.constructor = { XenserverPlugin p, MorpheusContext c ->
            Mock(XenserverProvisionProvider) {
                stopServer(_) >> ServiceResponse.success()
            }
        }
        expect:
        provider.stopServer(computeServer).success
    }

    def "deleteServer destroys VM and returns success"() {
        given:
        def computeServer = Mock(ComputeServer) {
            getCloud() >> Mock(Cloud)
            getExternalId() >> 'extid'
            getSnapshots() >> []
        }
        plugin.getAuthConfig(_) >> [:]

        // Mock all static methods to prevent real execution
        XenComputeUtility.metaClass.static.getVirtualMachine = { Map config, String id -> [success: true] }
        XenComputeUtility.metaClass.static.stopVm = { Map config, String id -> [success: true] }
        XenComputeUtility.metaClass.static.destroyVm = { Map config, String id -> [success: true] }

        when:
        def resp = provider.deleteServer(computeServer)

        then:
        resp.success
    }

    def "getProvisionProvider returns correct provider"() {
        given:
        def mockProvider = Mock(ProvisionProvider) {
            getCode() >> 'xen'
        }
        plugin.getProvidersByType(ProvisionProvider) >> [mockProvider]
        expect:
        provider.getProvisionProvider('xen') == mockProvider
    }

    def "getDefaultProvisionTypeCode returns XenserverProvisionProvider PROVIDER_CODE"() {
        expect:
        provider.getDefaultProvisionTypeCode() == XenserverProvisionProvider.PROVIDER_CODE
    }
}
