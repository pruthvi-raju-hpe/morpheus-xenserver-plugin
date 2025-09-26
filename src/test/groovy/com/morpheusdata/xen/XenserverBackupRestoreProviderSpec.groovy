package com.morpheusdata.xen

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.backup.response.BackupRestoreResponse
import com.morpheusdata.core.MorpheusAsyncServices
import com.morpheusdata.core.MorpheusWorkloadService
import com.morpheusdata.model.*
import com.morpheusdata.response.ServiceResponse
import com.morpheusdata.xen.util.XenComputeUtility
import io.reactivex.rxjava3.core.Maybe
import spock.lang.Specification
import spock.lang.Subject

class XenserverBackupRestoreProviderSpec extends Specification {

    @Subject
    XenserverBackupRestoreProvider provider

    XenserverPlugin mockPlugin
    MorpheusContext mockContext
    MorpheusAsyncServices mockAsync
    MorpheusWorkloadService mockWorkloadService

    def setup() {
        mockContext = Mock(MorpheusContext)
        mockAsync = Mock(MorpheusAsyncServices)
        mockWorkloadService = Mock(MorpheusWorkloadService)

        mockPlugin = Mock(XenserverPlugin)
        mockPlugin.getMorpheusContext() >> mockContext

        provider = new XenserverBackupRestoreProvider(mockPlugin, mockContext)
    }

    def "constructor should initialize plugin and morpheusContext"() {
        given:
        XenserverPlugin testPlugin = Mock(XenserverPlugin)
        MorpheusContext testContext = Mock(MorpheusContext)

        when:
        XenserverBackupRestoreProvider testProvider = new XenserverBackupRestoreProvider(testPlugin, testContext)

        then:
        testProvider.plugin == testPlugin
        testProvider.morpheusContext == testContext
    }

    def "getMorpheus should return morpheus context"() {
        expect:
        provider.getMorpheus() == mockContext
    }

    def "configureRestoreBackup should return success"() {
        given:
        BackupResult backupResult = new BackupResult(id: 1L)
        Map config = [:]
        Map opts = [:]

        when:
        ServiceResponse response = provider.configureRestoreBackup(backupResult, config, opts)

        then:
        response.success
    }

    def "configureRestoreBackup should handle null parameters"() {
        when:
        ServiceResponse response = provider.configureRestoreBackup(null, null, null)

        then:
        response.success
    }

    def "getBackupRestoreInstanceConfig should return success with restore config"() {
        given:
        BackupResult backupResult = new BackupResult(id: 1L)
        Instance instance = new Instance(id: 1L, name: "test-instance")
        Map restoreConfig = [name: "restored-instance", hostname: "restored-host"]
        Map opts = [:]

        when:
        ServiceResponse response = provider.getBackupRestoreInstanceConfig(backupResult, instance, restoreConfig, opts)

        then:
        response.success
        response.data == restoreConfig
    }

    def "getBackupRestoreInstanceConfig should handle null instance"() {
        given:
        BackupResult backupResult = new BackupResult(id: 1L)
        Map restoreConfig = [name: "restored-instance"]
        Map opts = [:]

        when:
        ServiceResponse response = provider.getBackupRestoreInstanceConfig(backupResult, null, restoreConfig, opts)

        then:
        response.success
        response.data == restoreConfig
    }

    def "getBackupRestoreInstanceConfig should handle empty restore config"() {
        given:
        BackupResult backupResult = new BackupResult(id: 1L)
        Instance instance = new Instance(id: 1L)
        Map restoreConfig = [:]
        Map opts = [:]

        when:
        ServiceResponse response = provider.getBackupRestoreInstanceConfig(backupResult, instance, restoreConfig, opts)

        then:
        response.success
        response.data == restoreConfig
    }

    def "validateRestoreBackup should return success"() {
        given:
        BackupResult backupResult = new BackupResult(id: 1L)
        Map opts = [:]

        when:
        ServiceResponse response = provider.validateRestoreBackup(backupResult, opts)

        then:
        response.success
    }

    def "validateRestoreBackup should handle null opts"() {
        given:
        BackupResult backupResult = new BackupResult(id: 1L)

        when:
        ServiceResponse response = provider.validateRestoreBackup(backupResult, null)

        then:
        response.success
    }

    def "getRestoreOptions should return success"() {
        given:
        Backup backup = new Backup(id: 1L)
        Map opts = [:]

        when:
        ServiceResponse response = provider.getRestoreOptions(backup, opts)

        then:
        response.success
    }

    def "getRestoreOptions should handle null backup"() {
        given:
        Map opts = [:]

        when:
        ServiceResponse response = provider.getRestoreOptions(null, opts)

        then:
        response.success
    }

    def "restoreBackup should handle missing snapshot gracefully"() {
        given:
        BackupRestore backupRestore = new BackupRestore(id: 1L)
        BackupResult backupResult = new BackupResult(
                id: 100L,
                snapshotId: null,
                containerId: 1L
        )
        Backup backup = new Backup(id: 1L)
        Map opts = [:]

        when:
        ServiceResponse response = provider.restoreBackup(backupRestore, backupResult, backup, opts)

        then:
        response != null
        response.data instanceof BackupRestoreResponse
        response.data.backupRestore == backupRestore
    }

    def "restoreBackup should successfully restore with valid snapshot"() {
        given:
        String snapshotId = "snap-123"
        String vmId = "vm-456"

        BackupRestore backupRestore = new BackupRestore(id: 1L)
        BackupResult backupResult = new BackupResult(
                id: 100L,
                snapshotId: snapshotId,
                containerId: 1L
        )
        backupResult.setConfigProperty("vmId", vmId)

        Backup backup = new Backup(id: 1L)
        Map opts = [:]

        ComputeServer server = new ComputeServer(id: 1L)
        Cloud cloud = new Cloud(id: 1L)
        server.cloud = cloud

        Workload workload = new Workload(id: 1L, server: server)

        Map authConfig = [username: "admin", password: "password", apiUrl: "http://xenserver"]

        and:
        mockContext.async >> mockAsync
        mockAsync.workload >> mockWorkloadService
        mockWorkloadService.get(1L) >> Maybe.just(workload)
        mockPlugin.getAuthConfig(cloud) >> authConfig

        GroovySpy(XenComputeUtility, global: true)
        XenComputeUtility.restoreServer(authConfig, snapshotId) >> [success: true]
        XenComputeUtility.startVm(authConfig, vmId) >> [success: true]

        when:
        ServiceResponse response = provider.restoreBackup(backupRestore, backupResult, backup, opts)

        then:
        response != null
        response.success
        response.data instanceof BackupRestoreResponse
        response.data.backupRestore.status.toString() == BackupResult.Status.SUCCEEDED.toString()
        response.data.updates == true
    }

    def "restoreBackup should use containerId from opts when provided"() {
        given:
        String snapshotId = "snap-123"
        String vmId = "vm-456"

        BackupRestore backupRestore = new BackupRestore(id: 1L)
        BackupResult backupResult = new BackupResult(
                id: 100L,
                snapshotId: snapshotId,
                containerId: 1L
        )
        backupResult.setConfigProperty("vmId", vmId)

        Backup backup = new Backup(id: 1L)
        Map opts = [containerId: 2L]

        ComputeServer server = new ComputeServer(id: 1L)
        Cloud cloud = new Cloud(id: 1L)
        server.cloud = cloud

        Workload workload = new Workload(id: 2L, server: server)

        Map authConfig = [username: "admin", password: "password"]

        and:
        mockContext.async >> mockAsync
        mockAsync.workload >> mockWorkloadService
        mockWorkloadService.get(2L) >> Maybe.just(workload)
        mockPlugin.getAuthConfig(cloud) >> authConfig

        GroovySpy(XenComputeUtility, global: true)
        XenComputeUtility.restoreServer(authConfig, snapshotId) >> [success: true]
        XenComputeUtility.startVm(authConfig, vmId) >> [success: true]

        when:
        ServiceResponse response = provider.restoreBackup(backupRestore, backupResult, backup, opts)

        then:
        response != null
        response.success
    }

    def "restoreBackup should handle restore failure"() {
        given:
        String snapshotId = "snap-123"
        String vmId = "vm-456"

        BackupRestore backupRestore = new BackupRestore(id: 1L)
        BackupResult backupResult = new BackupResult(
                id: 100L,
                snapshotId: snapshotId,
                containerId: 1L
        )
        backupResult.setConfigProperty("vmId", vmId)

        Backup backup = new Backup(id: 1L)
        Map opts = [:]

        ComputeServer server = new ComputeServer(id: 1L)
        Cloud cloud = new Cloud(id: 1L)
        server.cloud = cloud

        Workload workload = new Workload(id: 1L, server: server)

        Map authConfig = [username: "admin", password: "password"]

        and:
        mockContext.async >> mockAsync
        mockAsync.workload >> mockWorkloadService
        mockWorkloadService.get(1L) >> Maybe.just(workload)
        mockPlugin.getAuthConfig(cloud) >> authConfig

        GroovySpy(XenComputeUtility, global: true)
        XenComputeUtility.restoreServer(authConfig, snapshotId) >> [success: false]

        when:
        ServiceResponse response = provider.restoreBackup(backupRestore, backupResult, backup, opts)

        then:
        response != null
        response.data instanceof BackupRestoreResponse
        response.data.backupRestore.status.toString() == BackupResult.Status.FAILED.toString()
        response.data.updates == true
    }

    def "restoreBackup should handle exceptions gracefully"() {
        given:
        BackupRestore backupRestore = new BackupRestore(id: 1L)
        BackupResult backupResult = new BackupResult(
                id: 100L,
                snapshotId: "snap-123",
                containerId: 1L
        )
        Backup backup = new Backup(id: 1L)
        Map opts = [:]

        and:
        mockContext.async >> mockAsync
        mockAsync.workload >> mockWorkloadService
        mockWorkloadService.get(1L) >> { throw new RuntimeException("Test error") }

        when:
        ServiceResponse response = provider.restoreBackup(backupRestore, backupResult, backup, opts)

        then:
        response != null
        !response.success
        response.msg == "Test error"
    }

    def "restoreBackup should handle null workload"() {
        given:
        BackupRestore backupRestore = new BackupRestore(id: 1L)
        BackupResult backupResult = new BackupResult(
                id: 100L,
                snapshotId: "snap-123",
                containerId: 1L
        )
        backupResult.setConfigProperty("vmId", "vm-456")
        Backup backup = new Backup(id: 1L)
        Map opts = [:]

        and:
        mockContext.async >> mockAsync
        mockAsync.workload >> mockWorkloadService
        mockWorkloadService.get(1L) >> {
            return Maybe.create({ emitter ->
                try {
                    throw new NullPointerException("Cannot access server from null workload")
                } catch (Exception e) {
                    emitter.onError(e)
                }
            })
        }

        when:
        ServiceResponse response = provider.restoreBackup(backupRestore, backupResult, backup, opts)

        then:
        response != null
        !response.success
    }

    def "refreshBackupRestoreResult should return success"() {
        given:
        BackupRestore backupRestore = new BackupRestore(id: 1L)
        BackupResult backupResult = new BackupResult(id: 100L)

        when:
        ServiceResponse response = provider.refreshBackupRestoreResult(backupRestore, backupResult)

        then:
        response.success
    }

    def "refreshBackupRestoreResult should handle null parameters"() {
        when:
        ServiceResponse response = provider.refreshBackupRestoreResult(null, null)

        then:
        response.success
    }

    def "plugin should be accessible"() {
        expect:
        provider.plugin == mockPlugin
    }

    def "morpheusContext should be accessible"() {
        expect:
        provider.morpheusContext == mockContext
    }

    def "configureRestoreBackup should handle various config options"() {
        given:
        BackupResult backupResult = new BackupResult(id: 1L)
        Map config = [
                restoreType: "new",
                name: "restored-vm",
                targetCloud: 5L
        ]
        Map opts = [zone: "us-east-1"]

        when:
        ServiceResponse response = provider.configureRestoreBackup(backupResult, config, opts)

        then:
        response.success
    }

    def "validateRestoreBackup should handle different backup result states"() {
        given:
        BackupResult backupResult = new BackupResult(
                id: 1L,
                status: "SUCCEEDED",
                snapshotId: "snap-123"
        )
        Map opts = [validate: true]

        when:
        ServiceResponse response = provider.validateRestoreBackup(backupResult, opts)

        then:
        response.success
    }

    def "getRestoreOptions should handle backup with metadata"() {
        given:
        Backup backup = new Backup(
                id: 1L,
                name: "test-backup",
                enabled: true
        )
        Map opts = [includeMetadata: true]

        when:
        ServiceResponse response = provider.getRestoreOptions(backup, opts)

        then:
        response.success
    }

    def "restoreBackup should handle backup result with missing vmId"() {
        given:
        String snapshotId = "snap-123"

        BackupRestore backupRestore = new BackupRestore(id: 1L)
        BackupResult backupResult = new BackupResult(
                id: 100L,
                snapshotId: snapshotId,
                containerId: 1L
        )
        Backup backup = new Backup(id: 1L)
        Map opts = [:]

        ComputeServer server = new ComputeServer(id: 1L)
        Cloud cloud = new Cloud(id: 1L)
        server.cloud = cloud

        Workload workload = new Workload(id: 1L, server: server)

        Map authConfig = [username: "admin", password: "password"]

        and:
        mockContext.async >> mockAsync
        mockAsync.workload >> mockWorkloadService
        mockWorkloadService.get(1L) >> Maybe.just(workload)
        mockPlugin.getAuthConfig(cloud) >> authConfig

        GroovySpy(XenComputeUtility, global: true)
        XenComputeUtility.restoreServer(authConfig, snapshotId) >> [success: true]
        XenComputeUtility.startVm(authConfig, null) >> [success: true]

        when:
        ServiceResponse response = provider.restoreBackup(backupRestore, backupResult, backup, opts)

        then:
        response != null
        response.data instanceof BackupRestoreResponse
        response.success
    }

    def "getBackupRestoreInstanceConfig should handle complex restore config"() {
        given:
        BackupResult backupResult = new BackupResult(id: 1L, snapshotId: "snap-123")
        Instance instance = new Instance(
                id: 1L,
                name: "test-instance",
                hostName: "test-host"
        )
        Map restoreConfig = [
                name: "restored-instance",
                hostname: "restored-host",
                memory: 2048,
                cpus: 2,
                storage: 50
        ]
        Map opts = [preserveConfig: true]

        when:
        ServiceResponse response = provider.getBackupRestoreInstanceConfig(backupResult, instance, restoreConfig, opts)

        then:
        response.success
        response.data == restoreConfig
        response.data.name == "restored-instance"
        response.data.memory == 2048
    }
}
