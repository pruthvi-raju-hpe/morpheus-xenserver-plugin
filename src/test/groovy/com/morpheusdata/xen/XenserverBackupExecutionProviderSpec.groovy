package com.morpheusdata.xen

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.MorpheusServices
import com.morpheusdata.core.MorpheusAsyncServices
import com.morpheusdata.core.backup.response.BackupExecutionResponse
import com.morpheusdata.model.*
import com.morpheusdata.model.projection.SnapshotIdentityProjection
import com.morpheusdata.response.ServiceResponse
import com.morpheusdata.xen.util.XenComputeUtility
import com.bertramlabs.plugins.karman.CloudFile
import com.bertramlabs.plugins.karman.Directory
import com.bertramlabs.plugins.karman.StorageProvider
import io.reactivex.rxjava3.core.Single
import com.morpheusdata.core.backup.MorpheusBackupService
import com.morpheusdata.core.backup.MorpheusBackupResultService
import com.morpheusdata.core.MorpheusSnapshotService
import com.morpheusdata.core.MorpheusInstanceService
import com.morpheusdata.core.synchronous.backup.MorpheusSynchronousBackupService
import com.morpheusdata.core.synchronous.cloud.MorpheusSynchronousCloudService
import com.morpheusdata.core.synchronous.compute.MorpheusSynchronousComputeServerService
import com.morpheusdata.core.synchronous.MorpheusSynchronousSnapshotService
import com.morpheusdata.core.synchronous.MorpheusSynchronousWorkloadService
import spock.lang.Specification
import spock.lang.Subject

class XenserverBackupExecutionProviderSpec extends Specification {

    @Subject
    XenserverBackupExecutionProvider provider

    XenserverPlugin mockPlugin
    MorpheusContext mockContext

    def setup() {
        mockPlugin = Mock(XenserverPlugin)

        // Mock synchronous services for MorpheusServices
        def mockSyncBackupService = Mock(MorpheusSynchronousBackupService)
        def mockSyncWorkloadService = Mock(MorpheusSynchronousWorkloadService)
        def mockSyncCloudService = Mock(MorpheusSynchronousCloudService)
        def mockSyncComputeServerService = Mock(MorpheusSynchronousComputeServerService)
        def mockSyncSnapshotService = Mock(MorpheusSynchronousSnapshotService)

        // Mock async services for MorpheusAsyncServices
        def mockAsyncBackupService = Mock(MorpheusBackupService)
        def mockAsyncSnapshotService = Mock(MorpheusSnapshotService)
        def mockAsyncInstanceService = Mock(MorpheusInstanceService)
        def mockBackupResultService = Mock(MorpheusBackupResultService)

        def mockServices = Mock(MorpheusServices)
        mockServices.backup >> mockSyncBackupService
        mockServices.workload >> mockSyncWorkloadService
        mockServices.cloud >> mockSyncCloudService
        mockServices.computeServer >> mockSyncComputeServerService
        mockServices.snapshot >> mockSyncSnapshotService

        def mockAsync = Mock(MorpheusAsyncServices)
        mockAsync.instance >> mockAsyncInstanceService
        mockAsync.snapshot >> mockAsyncSnapshotService
        mockAsync.backup >> Mock(MorpheusBackupService) {
            backupResult >> mockBackupResultService
        }

        mockContext = Mock(MorpheusContext)
        mockContext.services >> mockServices
        mockContext.async >> mockAsync

        provider = new XenserverBackupExecutionProvider(mockPlugin, mockContext)
    }

    def "configureBackup should return success"() {
        given:
        Backup backup = new Backup()
        Map config = [:]
        Map opts = [:]

        when:
        ServiceResponse response = provider.configureBackup(backup, config, opts)

        then:
        response.success
        response.data == backup
    }

    def "validateBackup should return success"() {
        given:
        Backup backup = new Backup()
        Map config = [:]
        Map opts = [:]

        when:
        ServiceResponse response = provider.validateBackup(backup, config, opts)

        then:
        response.success
        response.data == backup
    }

    def "createBackup should return success"() {
        given:
        Backup backup = new Backup()
        Map opts = [:]

        when:
        ServiceResponse response = provider.createBackup(backup, opts)

        then:
        response.success
    }

    def "deleteBackup should return success"() {
        given:
        Backup backup = new Backup()
        Map opts = [:]

        when:
        ServiceResponse response = provider.deleteBackup(backup, opts)

        then:
        response.success
    }

    def "deleteBackupResult should successfully delete snapshot when snapshot exists"() {
        given:
        String snapshotId = "snap-123"
        Backup backup = new Backup(id: 1L, copyToStore: false)
        BackupResult backupResult = new BackupResult(
                id: 100L,
                snapshotId: snapshotId,
                containerId: 1L,
                zoneId: 1L
        )
        backupResult.backup = backup

        Cloud cloud = new Cloud(id: 1L)
        ComputeServer server = new ComputeServer(id: 1L)
        server.cloud = cloud
        Workload workload = new Workload(id: 1L)
        workload.server = server

        SnapshotIdentityProjection snapshotRecord = new SnapshotIdentityProjection(
                id: 1L,
                externalId: snapshotId
        )
        server.snapshots = [snapshotRecord]

        Map authConfig = [host: "xenserver.local", username: "admin"]
        Map opts = [containerId: 1L]

        and:
        mockContext.services.backup.get(backup.id) >> backup
        mockContext.services.workload.get(1L) >> workload
        mockPlugin.getAuthConfig(cloud) >> authConfig
        mockContext.services.computeServer.save(server) >> server
        mockContext.services.snapshot.remove(snapshotRecord) >> null

        GroovySpy(XenComputeUtility, global: true)
        XenComputeUtility.destroyVm(authConfig, snapshotId) >> [success: true]

        when:
        ServiceResponse response = provider.deleteBackupResult(backupResult, opts)

        then:
        response.success
    }

    def "deleteBackupResult should handle missing cloud gracefully"() {
        given:
        Backup backup = new Backup(id: 1L, copyToStore: false)
        BackupResult backupResult = new BackupResult(
                id: 100L,
                snapshotId: "snap-123",
                zoneId: null
        )
        backupResult.backup = backup

        and:
        mockContext.services.backup.get(backup.id) >> backup
        mockContext.services.workload.get(_) >> null

        when:
        ServiceResponse response = provider.deleteBackupResult(backupResult, [:])

        then:
        notThrown(Exception)
    }

    def "prepareExecuteBackup should return success"() {
        given:
        Backup backup = new Backup()
        Map opts = [:]

        when:
        ServiceResponse response = provider.prepareExecuteBackup(backup, opts)

        then:
        response.success
    }

    def "prepareBackupResult should return success"() {
        given:
        BackupResult backupResult = new BackupResult()
        Map opts = [:]

        when:
        ServiceResponse response = provider.prepareBackupResult(backupResult, opts)

        then:
        response.success
    }

    def "executeBackup should handle snapshot creation"() {
        given:
        Account account = new Account(id: 1L)
        Cloud cloud = new Cloud(id: 1L)
        ComputeServer server = new ComputeServer(
                id: 1L,
                externalId: "vm-123",
                name: "test-server",
                snapshots: []
        )
        server.account = account
        server.cloud = cloud

        Backup backup = new Backup(id: 1L, copyToStore: false, containerId: 1L, instanceId: 1L)
        backup.account = account
        BackupResult backupResult = new BackupResult(id: 100L, startDate: new Date())
        backupResult.backup = backup

        Map executionConfig = [
                backupConfig: [workingPath: "/tmp/backup"]
        ]
        Map authConfig = [host: "xenserver.local"]
        String snapshotId = "snap-new-123"

        and:
        mockPlugin.getAuthConfig(cloud) >> authConfig
        mockPlugin.morpheus >> mockContext
        mockContext.executeCommandOnServer(*_) >> Single.just([success: true])
        mockContext.async.backup.backupResult.save(_) >> Single.just(backupResult)
        mockContext.services.snapshot.create(_) >> { args ->
            Snapshot s = new Snapshot(id: 1L)
            s.account = args[0].account
            s.externalId = args[0].externalId
            s.name = args[0].name
            return s
        }
        mockContext.async.snapshot.addSnapshot(_, _) >> Single.just(true)

        GroovySpy(XenComputeUtility, global: true)
        XenComputeUtility.snapshotVm(_, server.externalId) >> [
                success: true,
                snapshotId: snapshotId,
                externalId: "vm-123"
        ]

        when:
        ServiceResponse<BackupExecutionResponse> response = provider.executeBackup(
                backup, backupResult, executionConfig, cloud, server, [:]
        )

        then:
        response.data.backupResult.status.toString() == "FAILED"
        1 * mockContext.services.snapshot.create(_)
        1 * mockContext.async.snapshot.addSnapshot(_, _)
    }

    def "refreshBackupResult should return success with backup result"() {
        given:
        BackupResult backupResult = new BackupResult(id: 1L)

        when:
        ServiceResponse<BackupExecutionResponse> response = provider.refreshBackupResult(backupResult)

        then:
        response.success
        response.data.backupResult == backupResult
    }

    def "cancelBackup should return success"() {
        given:
        BackupResult backupResult = new BackupResult()
        Map opts = [:]

        when:
        ServiceResponse response = provider.cancelBackup(backupResult, opts)

        then:
        response.success
    }

    def "extractBackup should return existing archive if already extracted"() {
        given:
        Account account = new Account(id: 1L)
        Backup backup = new Backup(id: 1L)
        backup.account = account

        BackupResult backupResult = new BackupResult(
                id: 100L,
                snapshotExtracted: true,
                resultPath: "bucket/backup.1",
                resultArchive: "backup.100.zip"
        )
        backupResult.backup = backup

        StorageBucket bucket = new StorageBucket(bucketName: "backup-bucket")
        StorageProvider storageProvider = Mock(StorageProvider)
        Directory directory = Mock(Directory)
        CloudFile cloudFile = Mock(CloudFile)

        and:
        mockContext.services.backup.get(backup.id) >> backup
        mockContext.services.backup.getBackupStorageBucket(account, backup.id) >> bucket
        mockContext.services.backup.getBackupStorageProvider(bucket.id) >> storageProvider
        storageProvider.getAt("bucket/backup.1") >> directory
        directory.getAt("backup.100.zip") >> cloudFile
        cloudFile.exists() >> true

        when:
        ServiceResponse response = provider.extractBackup(backupResult, [:])

        then:
        response.success
        response.data == backupResult
    }

    def "getMorpheus should return morpheus context"() {
        expect:
        provider.getMorpheus() == mockContext
    }

    def "deleteBackupResult should handle errors gracefully"() {
        given:
        Backup backup = new Backup(id: 1L)
        BackupResult backupResult = new BackupResult(id: 100L)
        backupResult.backup = backup

        and:
        mockContext.services.backup.get(_) >> { throw new RuntimeException("Database error") }

        when:
        ServiceResponse response = provider.deleteBackupResult(backupResult, [:])

        then:
        !response.success
    }

    def "executeBackup should set status to failed when snapshot fails"() {
        given:
        Account account = new Account(id: 1L)
        Cloud cloud = new Cloud(id: 1L)
        ComputeServer server = new ComputeServer(
                id: 1L,
                externalId: "vm-123",
                name: "test-server"
        )
        server.account = account
        server.cloud = cloud

        Backup backup = new Backup(id: 1L, copyToStore: false, containerId: 1L, instanceId: 1L)
        BackupResult backupResult = new BackupResult(id: 100L)
        backupResult.backup = backup

        Map executionConfig = [
                backupConfig: [workingPath: "/tmp/backup"]
        ]

        and:
        mockPlugin.getAuthConfig(cloud) >> [:]
        mockPlugin.morpheus >> mockContext
        mockContext.executeCommandOnServer(*_) >> Single.just([success: true])
        mockContext.async.backup.backupResult.save(_) >> Single.just(backupResult)

        GroovySpy(XenComputeUtility, global: true)
        XenComputeUtility.snapshotVm(_, _) >> [success: false]

        when:
        ServiceResponse<BackupExecutionResponse> response = provider.executeBackup(
                backup, backupResult, executionConfig, cloud, server, [:]
        )

        then:
        response.success == true
        response.data.backupResult.status.toString() == "FAILED"
    }
}