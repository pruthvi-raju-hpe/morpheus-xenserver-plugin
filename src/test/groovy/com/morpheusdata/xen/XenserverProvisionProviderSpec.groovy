package com.morpheusdata.xen

import com.morpheusdata.PrepareHostResponse
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.MorpheusServices
import com.morpheusdata.core.MorpheusAsyncServices
import com.morpheusdata.core.MorpheusComputeServerService
import com.morpheusdata.core.MorpheusStorageVolumeService
import com.morpheusdata.core.cloud.MorpheusCloudService
import com.morpheusdata.core.compute.MorpheusComputeServerInterfaceService
import com.morpheusdata.core.localization.MorpheusLocalizationService
import com.morpheusdata.core.data.DataQuery
import com.morpheusdata.model.*
import com.morpheusdata.model.provisioning.HostRequest
import com.morpheusdata.model.provisioning.WorkloadRequest
import com.morpheusdata.response.PrepareWorkloadResponse
import com.morpheusdata.response.ServiceResponse
import com.morpheusdata.xen.util.XenComputeUtility
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.core.Observable
import spock.lang.Specification
import spock.lang.Subject

/**
 * Comprehensive unit test coverage for XenserverProvisionProvider combining all test scenarios:
 * - Basic metadata and configuration tests
 * - Success path tests for workload operations
 * - Error and edge case tests
 * - Additional coverage for simple methods and flags
 */
class XenserverProvisionProviderSpec extends Specification {

    @Subject
    XenserverProvisionProvider provider

    XenserverPlugin mockPlugin
    MorpheusContext mockContext
    MorpheusServices mockServices
    MorpheusLocalizationService mockLocalization

    def setup() {
        mockPlugin = Mock(XenserverPlugin)
        mockContext = Mock(MorpheusContext)
        mockServices = Mock(MorpheusServices)
        mockLocalization = Mock(MorpheusLocalizationService)
        mockContext.services >> mockServices
        mockServices.localization >> mockLocalization
        provider = new XenserverProvisionProvider(mockPlugin, mockContext)
    }

    def buildServer(Boolean withExternalId = true) {
        Cloud cloud = new Cloud(id: 1)
        Account acct = new Account(id: 1)
        ServicePlan plan = new ServicePlan(id: 1, maxMemory: 1024L * 1024L * 1024L, maxCores: 1, maxStorage: 10L * 1024L * 1024L * 1024L)
        ComputeServer server = new ComputeServer(id: 10, name: 'test-server', account: acct, cloud: cloud, plan: plan)
        server.externalId = withExternalId ? 'vm-123' : null
        server.interfaces = []
        server.volumes = []
        return server
    }

    def buildWorkload(Boolean withExternalId = true) {
        ComputeServer server = buildServer(withExternalId)
        Workload workload = new Workload(id: 55, server: server)
        workload.instance = new Instance(id: 77, plan: server.plan)
        return workload
    }

    // ========================================================================
    // BASIC METADATA AND CONFIGURATION TESTS
    // ========================================================================

    def "get basic metadata and flags"() {
        expect:
        provider.code == 'xen'
        provider.name == 'XCP-ng'
        provider.getProvisionTypeCode() == 'xen'
        provider.hasNetworks()
        provider.canAddVolumes()
        provider.canCustomizeRootVolume()
        provider.hasDatastores()
        provider.customSupported()
        provider.supportsCustomServicePlans()
        provider.getDiskNameList().contains('xvdl')
        provider.getVirtualImageTypes()*.code.containsAll(['xen','vhd','xva'])
    }

    def "option types present"() {
        when:
        def opts = provider.getOptionTypes()
        def nodeOpts = provider.getNodeOptionTypes()
        then:
        opts.find { it.code == 'provisionType.xenserver.noAgent' }
        nodeOpts.find { it.code == 'provisionType.xen.custom.containerType.virtualImageId' }
    }

    def "getCircularIcon should return icon with correct paths"() {
        when:
        Icon icon = provider.getCircularIcon()

        then:
        icon != null
        icon.path == 'xcpng-circular-light.svg'
        icon.darkPath == 'xcpng-circular-dark.svg'
    }

    def "getServicePlans should return collection of service plans"() {
        when:
        Collection<ServicePlan> plans = provider.getServicePlans()

        then:
        plans != null
        plans.size() == 9
        plans.find { it.code == 'xen-vm-512' } != null
        plans.find { it.code == 'xen-vm-1024' } != null
        plans.find { it.code == 'internal-custom-xen' } != null
    }

    def "getServicePlans should have correct memory configurations"() {
        when:
        Collection<ServicePlan> plans = provider.getServicePlans()
        def plan512 = plans.find { it.code == 'xen-vm-512' }
        def plan1024 = plans.find { it.code == 'xen-vm-1024' }

        then:
        plan512.maxMemory == 512L * 1024L * 1024L
        plan1024.maxMemory == 1024L * 1024L * 1024L
    }

    def "getRootVolumeStorageTypes should return storage types"() {
        given:
        def mockStorageTypeService = Mock(com.morpheusdata.core.MorpheusStorageVolumeTypeService)
        def mockAsyncServices = Mock(MorpheusAsyncServices)
        def mockStorageVolumeService = Mock(MorpheusStorageVolumeService)
        mockContext.async >> mockAsyncServices
        mockAsyncServices.storageVolume >> mockStorageVolumeService
        mockStorageVolumeService.storageVolumeType >> mockStorageTypeService

        def storageType = new StorageVolumeType(code: 'standard', name: 'Standard')
        mockStorageTypeService.list(_ as DataQuery) >> Observable.just(storageType)

        when:
        Collection<StorageVolumeType> types = provider.getRootVolumeStorageTypes()

        then:
        types != null
        types.size() == 1
        types[0].code == 'standard'
    }

    def "getDataVolumeStorageTypes should return storage types"() {
        given:
        def mockStorageTypeService = Mock(com.morpheusdata.core.MorpheusStorageVolumeTypeService)
        def mockAsyncServices = Mock(MorpheusAsyncServices)
        def mockStorageVolumeService = Mock(MorpheusStorageVolumeService)
        mockContext.async >> mockAsyncServices
        mockAsyncServices.storageVolume >> mockStorageVolumeService
        mockStorageVolumeService.storageVolumeType >> mockStorageTypeService

        def storageType = new StorageVolumeType(code: 'standard', name: 'Standard')
        mockStorageTypeService.list(_ as DataQuery) >> Observable.just(storageType)

        when:
        Collection<StorageVolumeType> types = provider.getDataVolumeStorageTypes()

        then:
        types != null
        types.size() == 1
        types[0].code == 'standard'
    }

    def "getHostDiskMode should return correct mode"() {
        expect:
        provider.getHostDiskMode() == 'lvm'
    }

    def "getDeployTargetService should return correct service name"() {
        expect:
        provider.getDeployTargetService() == 'vmDeployTargetService'
    }

    def "getNodeFormat should return correct format"() {
        expect:
        provider.getNodeFormat() == 'vm'
    }

    def "lvmSupported should return true"() {
        expect:
        provider.lvmSupported() == true
    }

    def "hasComputeZonePools should return false"() {
        expect:
        provider.hasComputeZonePools() == false
    }

    def "canReconfigureNetwork should return true"() {
        expect:
        provider.canReconfigureNetwork() == true
    }

    def "aclEnabled should return false"() {
        expect:
        provider.aclEnabled() == false
    }

    def "multiTenant should return false"() {
        expect:
        provider.multiTenant() == false
    }

    // ========================================================================
    // WORKLOAD VALIDATION TESTS
    // ========================================================================

    def "validateWorkload success with empty opts"() {
        given:
        GroovySpy(XenComputeUtility, global: true)
        XenComputeUtility.validateServerConfig(_) >> [success:true]
        when:
        def resp = provider.validateWorkload([:])
        then:
        resp.success
    }

    def "validateWorkload returns errors when utility fails"() {
        given:
        GroovySpy(XenComputeUtility, global: true)
        XenComputeUtility.validateServerConfig(_) >> [success:false, errors:[[field:'imageId', msg:'Missing image']]]
        when:
        def resp = provider.validateWorkload([config:[imageId:null]])
        then:
        !resp.success || resp.errors?.imageId == 'Missing image'
    }

    def "validateHost custom template collects errors"() {
        given:
        GroovySpy(XenComputeUtility, global:true)
        XenComputeUtility.validateServerConfig(_) >> [success:false, errors:[[field:'imageId', msg:'Image required']]]
        when:
        def resp = provider.validateHost(new ComputeServer(), [config:[templateTypeSelect:'custom', imageId:null]])
        then:
        !resp.success
        resp.errors['imageId'] == 'Image required'
    }

    def "validateHost success no custom template"() {
        given:
        GroovySpy(XenComputeUtility, global:true)
        XenComputeUtility.validateServerConfig(_) >> [success:true]
        when:
        def resp = provider.validateHost(new ComputeServer(), [config:[templateTypeSelect:'default']])
        then:
        resp.success
    }

    // ========================================================================
    // WORKLOAD LIFECYCLE - SUCCESS PATHS
    // ========================================================================

    def "prepareWorkload should return success response"() {
        given:
        Workload workload = new Workload(id: 1L)
        WorkloadRequest workloadRequest = new WorkloadRequest()
        Map opts = [:]

        when:
        ServiceResponse<PrepareWorkloadResponse> response = provider.prepareWorkload(workload, workloadRequest, opts)

        then:
        response != null
        response.success == true
        response.data != null
        response.data.workload == workload
    }

    def "startWorkload success path"() {
        given:
        def workload = buildWorkload(true)
        mockPlugin.getAuthConfig(_) >> [endpoint:'xen']
        GroovySpy(XenComputeUtility, global: true)
        XenComputeUtility.startVm(_, _) >> [success:true]
        when:
        def resp = provider.startWorkload(workload)
        then:
        resp.success
    }

    def "stopWorkload success path"() {
        given:
        def workload = buildWorkload(true)
        mockPlugin.getAuthConfig(_) >> [:]
        GroovySpy(XenComputeUtility, global: true)
        XenComputeUtility.stopVm(_, _) >> [success:true]
        when:
        def resp = provider.stopWorkload(workload)
        then:
        resp.success
    }

    def "restartWorkload success path"() {
        given:
        def workload = buildWorkload(true)
        mockPlugin.getAuthConfig(_) >> [:]
        GroovySpy(XenComputeUtility, global: true)
        XenComputeUtility.restartVm(_, _) >> [success:true]
        when:
        def resp = provider.restartWorkload(workload)
        then:
        resp.success
    }

    def "removeWorkload success path"() {
        given:
        def workload = buildWorkload(true)
        provider = new XenserverProvisionProvider(mockPlugin, mockContext)
        provider = GroovySpy(provider.getClass(), constructorArgs: [mockPlugin, mockContext])
        provider.stopWorkload(_) >> ServiceResponse.success()
        mockPlugin.getAuthConfig(_) >> [:]
        GroovySpy(XenComputeUtility, global: true)
        XenComputeUtility.destroyVm(_, _) >> [success:true]
        when:
        def resp = provider.removeWorkload(workload, [:])
        then:
        resp.success
    }

    // ========================================================================
    // WORKLOAD LIFECYCLE - ERROR PATHS
    // ========================================================================

    def "startWorkload missing externalId returns error"() {
        given:
        def workload = buildWorkload(false)
        mockLocalization.get('gomorpheus.provision.xenServer.vmNotFound') >> 'VM Not Found'
        when:
        def resp = provider.startWorkload(workload)
        then:
        !resp.success
        resp.error == 'VM Not Found'
    }

    def "restartWorkload missing externalId returns error"() {
        given:
        def workload = buildWorkload(false)
        mockLocalization.get('gomorpheus.provision.xenServer.vmNotFound') >> 'VM Not Found'
        when:
        def resp = provider.restartWorkload(workload)
        then:
        !resp.success
        resp.error == 'VM Not Found'
    }

    def "stopWorkload missing externalId still success with message"() {
        given:
        def workload = buildWorkload(false)
        mockLocalization.get('gomorpheus.provision.xenServer.vmNotFound') >> 'VM Not Found'
        when:
        def resp = provider.stopWorkload(workload)
        then:
        resp.success
        resp.msg == 'VM Not Found'
    }

    def "removeWorkload missing externalId"() {
        given:
        def workload = buildWorkload(false)
        mockLocalization.get('gomorpheus.provision.xenServer.vmNotFound') >> 'VM Not Found'
        when:
        def resp = provider.removeWorkload(workload, [:])
        then:
        !resp.success
        resp.errors.error == 'VM Not Found'
    }

    def "removeWorkload destroy failure path"() {
        given:
        def workload = buildWorkload(true)
        provider = new XenserverProvisionProvider(mockPlugin, mockContext)
        provider = GroovySpy(provider.getClass(), constructorArgs: [mockPlugin, mockContext])
        provider.stopWorkload(_) >> ServiceResponse.success()
        mockPlugin.getAuthConfig(_) >> [:]
        GroovyMock(XenComputeUtility, global: true)
        XenComputeUtility.destroyVm(_, _) >> [success:false]
        mockLocalization.get('gomorpheus.provision.xenServer.failRemoveVm') >> 'Failed remove'
        when:
        def resp = provider.removeWorkload(workload, [:])
        then:
        !resp.success
        resp.errors.error == 'Failed remove'
    }

    def "removeWorkload exception path"() {
        given:
        def workload = buildWorkload(true)
        provider = new XenserverProvisionProvider(mockPlugin, mockContext)
        provider = GroovySpy(provider.getClass(), constructorArgs: [mockPlugin, mockContext])
        provider.stopWorkload(_) >> { throw new RuntimeException('boom') }
        mockPlugin.getAuthConfig(_) >> [:]
        GroovyMock(XenComputeUtility, global: true)
        XenComputeUtility.destroyVm(_, _) >> { throw new RuntimeException('fail') }
        mockLocalization.get('gomorpheus.provision.xenServer.error.removeWorkload') >> 'Remove error'
        when:
        def resp = provider.removeWorkload(workload, [:])
        then:
        !resp.success
        resp.errors.error == 'Remove error'
    }

    // ========================================================================
    // SERVER LIFECYCLE TESTS
    // ========================================================================

    def "startServer success"() {
        given:
        ComputeServer server = new ComputeServer(id:1, externalId:'vm123', cloud:new Cloud(id:1))
        mockPlugin.getAuthConfig(_) >> [:]
        GroovySpy(XenComputeUtility, global:true)
        XenComputeUtility.startVm(_, _) >> [success:true]
        when:
        def resp = provider.startServer(server)
        then:
        resp.success
    }

    def "stopServer vm not found returns msg"() {
        when:
        def resp = provider.stopServer(new ComputeServer())
        then:
        !resp.success
    }

    // ========================================================================
    // HOST PROVISIONING TESTS
    // ========================================================================

    def "prepareHost returns success when sourceImage already set"() {
        given:
        ComputeServer server = new ComputeServer(id: 1L, sourceImage: new VirtualImage(id: 100L))
        HostRequest hostRequest = new HostRequest()
        Map opts = [:]

        when:
        ServiceResponse<PrepareHostResponse> response = provider.prepareHost(server, hostRequest, opts)

        then:
        response != null
        response.success == true
    }

    def "prepareHost missing virtual image sets msg"() {
        given:
        def server = new ComputeServer(id:301L)
        mockServices.computeTypeSet >> [get:{ Long id -> null }]
        when:
        def resp = provider.prepareHost(server, new HostRequest(), [:])
        then:
        !resp.success
        resp.msg == 'No virtual image selected'
    }

    def "finalizeHost success path with no interfaces"() {
        given:
        ComputeServer server = new ComputeServer(id:77, externalId:'vm-final', cloud:new Cloud(id:1))
        mockPlugin.getAuthConfig(_) >> [:]

        def mockComputeServerInterfaceService = Mock(MorpheusComputeServerInterfaceService)
        def mockAsyncServices = Mock(MorpheusAsyncServices)
        def mockComputeServerAsync = Mock(MorpheusComputeServerService)
        def mockComputeServerSyncService = Mock(com.morpheusdata.core.synchronous.compute.MorpheusSynchronousComputeServerService)

        mockContext.async >> mockAsyncServices
        mockAsyncServices.computeServer >> mockComputeServerAsync
        mockComputeServerAsync.computeServerInterface >> mockComputeServerInterfaceService
        mockComputeServerInterfaceService.get(_) >> Single.just(new ComputeServerInterface())
        mockComputeServerInterfaceService.save(_) >> Single.just([success:true])
        mockContext.services >> mockServices
        mockServices.computeServer >> mockComputeServerSyncService
        mockComputeServerSyncService.get(_) >> server

        def spyProvider = Spy(XenserverProvisionProvider, constructorArgs:[mockPlugin, mockContext])
        spyProvider.checkServerReady(_) >> [success:true, ipAddresses:[:]]
        when:
        def resp = spyProvider.finalizeHost(server)
        then:
        resp.success
    }

    def "waitForHost success path"() {
        given:
        ComputeServer server = new ComputeServer(id:88, externalId:'vm-wait', cloud:new Cloud(id:1))
        mockPlugin.getAuthConfig(_) >> [:]
        def spyProvider = GroovySpy(XenserverProvisionProvider, constructorArgs:[mockPlugin, mockContext])
        spyProvider.checkServerReady(_) >> [success:true, ipAddresses:[:]]
        spyProvider.finalizeHost(_) >> ServiceResponse.success()
        when:
        def resp = spyProvider.waitForHost(server)
        then:
        resp.success
    }

    // ========================================================================
    // STORAGE VOLUME TESTS
    // ========================================================================

    def "setVolumeInfo matches by internalId"() {
        given:
        def vol = new StorageVolume(id:1L, internalId:'vol-uuid-1', displayOrder:0, rootVolume:true)
        def externalVolumes = [[uuid:'vol-uuid-1', deviceIndex:0]]

        def mockStorageVolumeService = Mock(MorpheusStorageVolumeService)
        def mockAsyncServices = Mock(MorpheusAsyncServices)

        mockContext.async >> mockAsyncServices
        mockAsyncServices.storageVolume >> mockStorageVolumeService
        mockStorageVolumeService.save(_) >> Single.just(true)

        when:
        provider.setVolumeInfo([vol], externalVolumes)
        then:
        vol.externalId == '0'
        vol.unitNumber == '0'
    }

    def "setVolumeInfo matches by unitNumber when no internalId"() {
        given:
        def vol = new StorageVolume(id:2L, unitNumber:'1', displayOrder:1, rootVolume:false)
        def externalVolumes = [[uuid:'vol-uuid-2', deviceIndex:1]]

        def mockStorageVolumeService = Mock(MorpheusStorageVolumeService)
        def mockAsyncServices = Mock(MorpheusAsyncServices)

        mockContext.async >> mockAsyncServices
        mockAsyncServices.storageVolume >> mockStorageVolumeService
        mockStorageVolumeService.save(_) >> Single.just(true)

        when:
        provider.setVolumeInfo([vol], externalVolumes)
        then:
        vol.externalId == '1'
        vol.internalId == 'vol-uuid-2'
    }

    // ========================================================================
    // CONSOLE ACCESS TESTS
    // ========================================================================

    def "getXvpVNCConsoleUrl failure"() {
        given:
        def server = buildServer(true)
        mockPlugin.getAuthConfig(_) >> [:]
        GroovyMock(XenComputeUtility, global: true)
        XenComputeUtility.getConsoles(_, _) >> [success:false]
        when:
        def resp = provider.getXvpVNCConsoleUrl(server)
        then:
        !resp.success
    }

    // ========================================================================
    // CONSTRUCTOR AND INTERFACE TESTS
    // ========================================================================

    def "constructor should initialize context and plugin"() {
        expect:
        provider.@context == mockContext
        provider.@plugin == mockPlugin
    }

    def "provider should implement required interfaces"() {
        expect:
        provider instanceof com.morpheusdata.core.providers.WorkloadProvisionProvider
        provider instanceof com.morpheusdata.core.providers.HostProvisionProvider
        provider instanceof com.morpheusdata.core.providers.ProvisionProvider.BlockDeviceNameFacet
    }

    // ========================================================================
    // ADDITIONAL COVERAGE - SIMPLE UTILITY METHODS
    // ========================================================================


    def "canResizeRootVolume should return true"() {
        expect:
        provider.canResizeRootVolume() == true
    }

    def "supportsAgent should return true"() {
        expect:
        provider.supportsAgent() == true
    }

    def "getVirtualImageTypes should include expected types"() {
        when:
        Collection<VirtualImageType> types = provider.getVirtualImageTypes()

        then:
        types != null
        types.size() >= 3
        types.find { it.code == 'xen' } != null
        types.find { it.code == 'vhd' } != null
        types.find { it.code == 'xva' } != null
    }

    def "getNodeOptionTypes should have all required options"() {
        when:
        Collection<OptionType> nodeOpts = provider.getNodeOptionTypes()

        then:
        nodeOpts.size() >= 10
        nodeOpts.find { it.code == 'provisionType.xen.custom.containerType.virtualImageId' } != null
        nodeOpts.find { it.code == 'provisionType.xen.custom.containerType.osTypeId' } != null
        nodeOpts.find { it.code == 'provisionType.xen.custom.containerType.mountLogs' } != null
        nodeOpts.find { it.code == 'provisionType.xen.custom.containerType.mountConfig' } != null
        nodeOpts.find { it.code == 'provisionType.xen.custom.containerType.mountData' } != null
        nodeOpts.find { it.code == 'provisionType.xen.custom.instanceType.backupType' } != null
        nodeOpts.find { it.code == 'provisionType.xen.custom.containerType.statTypeCode' } != null
        nodeOpts.find { it.code == 'provisionType.xen.custom.containerType.logTypeCode' } != null
        nodeOpts.find { it.code == 'provisionType.xen.custom.containerType.serverType' } != null
        nodeOpts.find { it.code == 'provisionType.xen.custom.instanceTypeLayout.description' } != null
    }

    def "getOptionTypes should return skip agent option with correct properties"() {
        when:
        Collection<OptionType> opts = provider.getOptionTypes()
        def noAgentOpt = opts.find { it.code == 'provisionType.xenserver.noAgent' }

        then:
        noAgentOpt != null
        noAgentOpt.name == 'skip agent install'
        noAgentOpt.fieldName == 'noAgent'
        noAgentOpt.inputType == OptionType.InputType.CHECKBOX
        noAgentOpt.fieldContext == 'config'
        noAgentOpt.displayOrder == 4
        noAgentOpt.required == false
    }

    def "getServicePlans should have all expected plans"() {
        when:
        Collection<ServicePlan> plans = provider.getServicePlans()

        then:
        plans.size() == 9
        plans.find { it.code == 'xen-vm-512' } != null
        plans.find { it.code == 'xen-vm-1024' } != null
        plans.find { it.code == 'xen-vm-2048' } != null
        plans.find { it.code == 'xen-vm-4096' } != null
        plans.find { it.code == 'xen-vm-8192' } != null
        plans.find { it.code == 'xen-vm-16384' } != null
        plans.find { it.code == 'xen-vm-24576' } != null
        plans.find { it.code == 'xen-vm-32768' } != null
        plans.find { it.code == 'internal-custom-xen' } != null
    }

    def "service plan configurations should be correct"() {
        when:
        Collection<ServicePlan> plans = provider.getServicePlans()

        then:
        def plan512 = plans.find { it.code == 'xen-vm-512' }
        plan512.maxMemory == 512L * 1024L * 1024L
        plan512.maxCores == 1
        plan512.maxStorage == 10L * 1024L * 1024L * 1024L
        plan512.editable == true
        plan512.customMaxStorage == true
        plan512.customMaxDataStorage == true
        plan512.addVolumes == true

        def customPlan = plans.find { it.code == 'internal-custom-xen' }
        customPlan.customCpu == true
        customPlan.customCores == true
        customPlan.customMaxMemory == true
        customPlan.deletable == false
        customPlan.provisionable == false
    }

    def "getDiskNameList should contain all expected disk names"() {
        when:
        List<String> diskNames = provider.getDiskNameList()

        then:
        diskNames.size() == 11
        diskNames == ['xvda', 'xvdc', 'xvdd', 'xvde', 'xvdf', 'xvdg', 'xvdh', 'xvdi', 'xvdj', 'xvdk', 'xvdl']
    }
}
