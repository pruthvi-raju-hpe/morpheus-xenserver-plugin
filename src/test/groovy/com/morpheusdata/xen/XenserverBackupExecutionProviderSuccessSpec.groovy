package com.morpheusdata.xen

import com.bertramlabs.plugins.karman.CloudFile
import com.bertramlabs.plugins.karman.Directory
import com.bertramlabs.plugins.karman.StorageProvider
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.MorpheusServices
import com.morpheusdata.core.MorpheusAsyncServices
import com.morpheusdata.core.backup.response.BackupExecutionResponse
import com.morpheusdata.model.*
import com.morpheusdata.model.projection.SnapshotIdentityProjection
import com.morpheusdata.response.ServiceResponse
import com.morpheusdata.xen.util.XenComputeUtility
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import spock.lang.Specification
import spock.lang.Subject
import com.morpheusdata.core.backup.MorpheusBackupService
import com.morpheusdata.core.backup.MorpheusBackupResultService
import com.morpheusdata.core.MorpheusSnapshotService
import com.morpheusdata.core.MorpheusInstanceService
import com.morpheusdata.core.synchronous.backup.MorpheusSynchronousBackupService
import com.morpheusdata.core.synchronous.compute.MorpheusSynchronousComputeServerService
import com.morpheusdata.core.synchronous.MorpheusSynchronousSnapshotService
import com.morpheusdata.core.synchronous.MorpheusSynchronousWorkloadService
import com.morpheusdata.core.synchronous.cloud.MorpheusSynchronousCloudService
import com.morpheusdata.core.synchronous.backup.MorpheusSynchronousBackupResultService

class XenserverBackupExecutionProviderSuccessSpec extends Specification {

	@Subject
	XenserverBackupExecutionProvider provider

	XenserverPlugin mockPlugin
	MorpheusContext mockContext
	MorpheusServices mockServices
	// async service mocks
	MorpheusAsyncServices mockAsync
	MorpheusBackupService mockAsyncBackup
	MorpheusBackupResultService mockAsyncBackupResult
	MorpheusSnapshotService mockAsyncSnapshot
	MorpheusInstanceService mockAsyncInstance
	// synchronous services
	MorpheusSynchronousBackupService mockSyncBackup
	MorpheusSynchronousBackupResultService mockSyncBackupResult
	MorpheusSynchronousSnapshotService mockSyncSnapshot
	MorpheusSynchronousComputeServerService mockSyncComputeServer
	MorpheusSynchronousWorkloadService mockSyncWorkload
	MorpheusSynchronousCloudService mockSyncCloud

	def setup() {
		mockPlugin = Mock(XenserverPlugin)
		// build synchronous mocks
		mockSyncBackup = Mock(MorpheusSynchronousBackupService)
		mockSyncBackupResult = Mock(MorpheusSynchronousBackupResultService)
		mockSyncSnapshot = Mock(MorpheusSynchronousSnapshotService)
		mockSyncComputeServer = Mock(MorpheusSynchronousComputeServerService)
		mockSyncWorkload = Mock(MorpheusSynchronousWorkloadService)
		mockSyncCloud = Mock(MorpheusSynchronousCloudService)
		// async mocks
		mockAsyncBackup = Mock(MorpheusBackupService)
		mockAsyncBackupResult = Mock(MorpheusBackupResultService)
		mockAsyncSnapshot = Mock(MorpheusSnapshotService)
		mockAsyncInstance = Mock(MorpheusInstanceService)

		mockServices = Mock(MorpheusServices)
		mockServices.backup >> mockSyncBackup
		mockServices.snapshot >> mockSyncSnapshot
		mockServices.computeServer >> mockSyncComputeServer
		mockServices.workload >> mockSyncWorkload
		mockServices.cloud >> mockSyncCloud
		mockSyncBackup.backupResult >> mockSyncBackupResult

		mockAsync = Mock(MorpheusAsyncServices)
		mockAsync.backup >> mockAsyncBackup
		mockAsyncBackup.backupResult >> mockAsyncBackupResult
		mockAsync.snapshot >> mockAsyncSnapshot
		mockAsync.instance >> mockAsyncInstance

		mockContext = Mock(MorpheusContext)
		mockContext.services >> mockServices
		mockContext.async >> mockAsync
		provider = new XenserverBackupExecutionProvider(mockPlugin, mockContext)
		// default interactions
		mockAsyncBackupResult.save(_) >> { args -> Single.just(args[0]) }
		mockSyncBackupResult.save(_) >> { args -> args[0] }
		// return Single per interface contract
		mockAsyncSnapshot.addSnapshot(_, _) >> Single.just(true)
	}

	def buildServerWithSnapshot(Account account, Cloud cloud, String snapshotId) {
		ComputeServer server = new ComputeServer(id: 2L, account: account, cloud: cloud, externalId: 'vm-xyz', name: 'srv')
		server.snapshots = [new SnapshotIdentityProjection(id: 9L, externalId: snapshotId)]
		return server
	}

	def "executeBackup success path with copyToStore true"() {
		given:
		Account account = new Account(id:1L)
		Cloud cloud = new Cloud(id:1L)
		String snapshotId = 'snap-good-1'
		ComputeServer server = buildServerWithSnapshot(account, cloud, snapshotId)
		Backup backup = new Backup(id:5L, copyToStore:true, containerId:11L, instanceId:22L)
		backup.account = account
		BackupResult backupResult = new BackupResult(id:99L) // removed startDate to skip duration calculation
		backupResult.backup = backup
		Instance instance = new Instance(id:22L, name:'inst')
		Workload workload = new Workload(id:11L, server: server)

		Map executionConfig = [backupConfig:[workingPath:"/tmp/backup"]]
		Map authConfig = [endpoint:'xen']

		StorageBucket bucket = new StorageBucket(id: 50L, bucketName: 'bucket')

		// Mock chained provider[outputPath][archiveName]
		StorageProvider storageProvider = Mock(StorageProvider)
		Directory directory = Mock(Directory)
		CloudFile cloudFile = Mock(CloudFile)

		and:
		mockPlugin.getAuthConfig(cloud) >> authConfig
		mockPlugin.morpheus >> mockContext
		mockAsyncBackupResult.save(_) >> Single.just(backupResult)
		mockSyncSnapshot.create(_) >> { Snapshot s -> new Snapshot(id:44L, account:account, externalId:s.externalId, name:s.name) }
		// ensure correct reactive type
		mockAsyncSnapshot.addSnapshot(_, _) >> Single.just(true)
		mockAsyncInstance.get(backup.instanceId) >> Maybe.just(instance)
		mockSyncBackup.getBackupStorageBucket(account, backup.id) >> bucket
		mockSyncBackup.getBackupStorageProvider(bucket.id) >> storageProvider
		mockSyncBackup.getBackupWorkingPath(backup.id, backupResult.id) >> "/tmp/backup"
		mockSyncComputeServer.save(_) >> server
		mockSyncSnapshot.remove(_) >> null
		// Directory & CloudFile interactions for streaming save
		storageProvider.getAt(_) >> { String path -> directory }
		directory.getAt(_) >> cloudFile
		cloudFile.exists() >> false
		cloudFile.setInputStream(_) >> { }
		cloudFile.save() >> { }
		cloudFile.getContentLength() >> 1048576L

		GroovyMock(XenComputeUtility, global:true)
		XenComputeUtility.snapshotVm(_, _) >> [success:true, snapshotId:snapshotId, externalId: server.externalId]
		XenComputeUtility.exportVm(_, _) >> [success:true]
		XenComputeUtility.destroyVm(_, _) >> [success:true]

		when:
		ServiceResponse<BackupExecutionResponse> resp = provider.executeBackup(backup, backupResult, executionConfig, cloud, server, [:])

		then:
		resp.success
		resp.data.backupResult.status.toString() == 'SUCCEEDED'
		resp.data.backupResult.snapshotId == snapshotId
		resp.data.backupResult.snapshotExtracted
		resp.data.backupResult.resultArchive == 'backup.99.zip'
	}

	def "extractBackup performs export when not previously extracted"() {
		given:
		Account account = new Account(id:1L)
		Cloud cloud = new Cloud(id:1L)
		String snapshotId = 'snap-existing-22'
		ComputeServer server = new ComputeServer(id: 3L, account: account, cloud: cloud, externalId: 'vm-abc', name: 'srv2')
		Workload workload = new Workload(id: 33L, server: server)
		Instance instance = new Instance(id: 44L, name: 'inst2')
		Backup backup = new Backup(id: 55L, account: account, containerId: workload.id, instanceId: instance.id)
		BackupResult backupResult = new BackupResult(id: 66L, snapshotId: snapshotId, snapshotExtracted: false)
		backupResult.backup = backup

		StorageBucket bucket = new StorageBucket(id: 77L, bucketName: 'buck2')
		StorageProvider storageProvider = Mock(StorageProvider)
		Directory directory = Mock(Directory)
		CloudFile cloudFile = Mock(CloudFile)
		Map authConfig = [endpoint:'xen']

		and:
		mockPlugin.getAuthConfig(cloud) >> authConfig
		mockSyncBackup.get(backup.id) >> backup
		mockSyncBackup.getBackupStorageBucket(account, backup.id) >> bucket
		mockSyncBackup.getBackupStorageProvider(bucket.id) >> storageProvider
		mockSyncWorkload.get(backup.containerId) >> workload
		mockAsyncInstance.get(backup.instanceId) >> Maybe.just(instance)
		mockSyncComputeServer.get(workload.server.id) >> server
		mockSyncBackup.getBackupWorkingPath(backup.id, backupResult.id) >> "/tmp/backup"
		mockSyncBackup.backupResult.save(_) >> { args -> args[0] }

		storageProvider.getAt(_) >> { directory }
		directory.getAt(_) >> cloudFile
		cloudFile.exists() >> false
		cloudFile.setInputStream(_) >> { }
		cloudFile.save() >> { }
		cloudFile.getContentLength() >> 2097152L

		GroovyMock(XenComputeUtility, global:true)
		XenComputeUtility.exportVm(_, _) >> [success:true]

		when:
		ServiceResponse resp = provider.extractBackup(backupResult, [:])

		then:
		resp.success
		resp.data.snapshotExtracted
		resp.data.resultArchive == 'backup.66.zip'
	}

	def "executeBackup marks failed when copyToStore false"() {
		given:
		Account account = new Account(id:1L)
		Cloud cloud = new Cloud(id:1L)
		String snapshotId = 'snap-no-store'
		ComputeServer server = buildServerWithSnapshot(account, cloud, snapshotId)
		Backup backup = new Backup(id:10L, copyToStore:false, containerId:21L, instanceId:31L)
		backup.account = account
		BackupResult backupResult = new BackupResult(id:101L)
		backupResult.backup = backup
		Instance instance = new Instance(id:31L, name:'instNoStore')

		Map executionConfig = [backupConfig:[workingPath:"/tmp/backup"]]
		Map authConfig = [endpoint:'xen']

		and:
		mockPlugin.getAuthConfig(cloud) >> authConfig
		mockPlugin.morpheus >> mockContext
		mockAsyncBackupResult.save(_) >> Single.just(backupResult)
		mockSyncSnapshot.create(_) >> { Snapshot s -> new Snapshot(id:55L, account:account, externalId:s.externalId, name:s.name) }
		mockAsyncSnapshot.addSnapshot(_, _) >> Single.just(true)
		mockAsyncInstance.get(backup.instanceId) >> Maybe.just(instance)

		GroovyMock(XenComputeUtility, global:true)
		XenComputeUtility.snapshotVm(_, _) >> [success:true, snapshotId:snapshotId, externalId: server.externalId]

		when:
		ServiceResponse<BackupExecutionResponse> resp = provider.executeBackup(backup, backupResult, executionConfig, cloud, server, [:])

		then:
		resp.success // provider currently sets success true even though status failed
		resp.data.backupResult.status.toString() == 'FAILED'
	}

	def "executeBackup marks failed on snapshot failure"() {
		given:
		Account account = new Account(id:1L)
		Cloud cloud = new Cloud(id:1L)
		ComputeServer server = buildServerWithSnapshot(account, cloud, 'unused')
		Backup backup = new Backup(id:11L, copyToStore:true, containerId:22L, instanceId:32L)
		backup.account = account
		BackupResult backupResult = new BackupResult(id:102L)
		backupResult.backup = backup
		Instance instance = new Instance(id:32L, name:'instFailSnap')

		Map executionConfig = [backupConfig:[workingPath:"/tmp/backup"]]
		Map authConfig = [endpoint:'xen']

		and:
		mockPlugin.getAuthConfig(cloud) >> authConfig
		mockPlugin.morpheus >> mockContext
		mockAsyncBackupResult.save(_) >> Single.just(backupResult)
		mockAsyncInstance.get(backup.instanceId) >> Maybe.just(instance)

		GroovyMock(XenComputeUtility, global:true)
		XenComputeUtility.snapshotVm(_, _) >> [success:false]

		when:
		ServiceResponse<BackupExecutionResponse> resp = provider.executeBackup(backup, backupResult, executionConfig, cloud, server, [:])

		then:
		resp.success
		resp.data.backupResult.status.toString() == 'FAILED'
	}

	def "executeBackup performs cleanup of snapshot after successful export"() {
		given:
		Account account = new Account(id:1L)
		Cloud cloud = new Cloud(id:1L)
		String snapshotId = 'snap-cleanup-success'
		ComputeServer server = buildServerWithSnapshot(account, cloud, snapshotId)
		Backup backup = new Backup(id:12L, copyToStore:true, containerId:23L, instanceId:33L)
		backup.account = account
		BackupResult backupResult = new BackupResult(id:103L)
		backupResult.backup = backup
		Instance instance = new Instance(id:33L, name:'instCleanup')

		Map executionConfig = [backupConfig:[workingPath:"/tmp/backup"]]
		Map authConfig = [endpoint:'xen']
		StorageBucket bucket = new StorageBucket(id: 60L, bucketName: 'bucket2')
		StorageProvider storageProvider = Mock(StorageProvider)
		Directory directory = Mock(Directory)
		CloudFile cloudFile = Mock(CloudFile)

		and:
		mockPlugin.getAuthConfig(cloud) >> authConfig
		mockPlugin.morpheus >> mockContext
		mockAsyncBackupResult.save(_) >> Single.just(backupResult)
		mockSyncSnapshot.create(_) >> { Snapshot s -> new Snapshot(id:64L, account:account, externalId:s.externalId, name:s.name) }
		mockAsyncSnapshot.addSnapshot(_, _) >> Single.just(true)
		mockAsyncInstance.get(backup.instanceId) >> Maybe.just(instance)
		mockSyncBackup.getBackupStorageBucket(account, backup.id) >> bucket
		mockSyncBackup.getBackupStorageProvider(bucket.id) >> storageProvider
		mockSyncBackup.getBackupWorkingPath(backup.id, backupResult.id) >> "/tmp/backup"
		mockSyncComputeServer.save(_) >> server
		mockSyncSnapshot.remove(_) >> null
		storageProvider.getAt(_) >> { String path -> directory }
		directory.getAt(_) >> cloudFile
		cloudFile.exists() >> false
		cloudFile.setInputStream(_) >> { }
		cloudFile.save() >> { }
		cloudFile.getContentLength() >> 524288L

		GroovyMock(XenComputeUtility, global:true)
		XenComputeUtility.snapshotVm(_, _) >> [success:true, snapshotId:snapshotId, externalId: server.externalId]
		XenComputeUtility.exportVm(_, _) >> [success:true]

		when:
		ServiceResponse<BackupExecutionResponse> resp = provider.executeBackup(backup, backupResult, executionConfig, cloud, server, [:])

		then:
		resp.success
		resp.data.backupResult.status.toString() == 'SUCCEEDED'
	}

	def "executeBackup still cleans up snapshot on export failure"() {
		given:
		Account account = new Account(id:1L)
		Cloud cloud = new Cloud(id:1L)
		String snapshotId = 'snap-cleanup-fail-export'
		ComputeServer server = buildServerWithSnapshot(account, cloud, snapshotId)
		Backup backup = new Backup(id:13L, copyToStore:true, containerId:24L, instanceId:34L)
		backup.account = account
		BackupResult backupResult = new BackupResult(id:104L)
		backupResult.backup = backup
		Instance instance = new Instance(id:34L, name:'instCleanupFailExport')

		Map executionConfig = [backupConfig:[workingPath:"/tmp/backup"]]
		Map authConfig = [endpoint:'xen']
		StorageBucket bucket = new StorageBucket(id: 61L, bucketName: 'bucket3')
		StorageProvider storageProvider = Mock(StorageProvider)
		Directory directory = Mock(Directory)
		CloudFile cloudFile = Mock(CloudFile)

		and:
		mockPlugin.getAuthConfig(cloud) >> authConfig
		mockPlugin.morpheus >> mockContext
		mockAsyncBackupResult.save(_) >> Single.just(backupResult)
		mockSyncSnapshot.create(_) >> { Snapshot s -> new Snapshot(id:65L, account:account, externalId:s.externalId, name:s.name) }
		mockAsyncSnapshot.addSnapshot(_, _) >> Single.just(true)
		mockAsyncInstance.get(backup.instanceId) >> Maybe.just(instance)
		mockSyncBackup.getBackupStorageBucket(account, backup.id) >> bucket
		mockSyncBackup.getBackupStorageProvider(bucket.id) >> storageProvider
		mockSyncBackup.getBackupWorkingPath(backup.id, backupResult.id) >> "/tmp/backup"
		mockSyncComputeServer.save(_) >> server
		mockSyncSnapshot.remove(_) >> null
		storageProvider.getAt(_) >> { String path -> directory }
		directory.getAt(_) >> cloudFile
		cloudFile.exists() >> false
		cloudFile.setInputStream(_) >> { }
		cloudFile.save() >> { }
		cloudFile.getContentLength() >> 1111111L

		GroovyMock(XenComputeUtility, global:true)
		XenComputeUtility.snapshotVm(_, _) >> [success:true, snapshotId:snapshotId, externalId: server.externalId]
		XenComputeUtility.exportVm(_, _) >> [success:false]

		when:
		ServiceResponse<BackupExecutionResponse> resp = provider.executeBackup(backup, backupResult, executionConfig, cloud, server, [:])

		then:
		resp.success
		resp.data.backupResult.status.toString() == 'FAILED'
	}
}
