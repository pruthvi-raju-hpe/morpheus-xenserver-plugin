package com.morpheusdata.xen

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.MorpheusServices
import com.morpheusdata.core.MorpheusAsyncServices
import com.morpheusdata.model.BackupProvider as BackupProviderModel
import com.morpheusdata.model.OptionType
import com.morpheusdata.response.ServiceResponse
import spock.lang.Specification
import spock.lang.Subject

class XenserverBackupTypeProviderSpec extends Specification {

	@Subject
	XenserverBackupTypeProvider provider

	XenserverPlugin mockPlugin
	MorpheusContext mockContext

	def setup() {
		mockPlugin = Mock(XenserverPlugin)

		def mockServices = Mock(MorpheusServices)
		def mockAsync = Mock(MorpheusAsyncServices)

		mockContext = Mock(MorpheusContext)
		mockContext.services >> mockServices
		mockContext.async >> mockAsync

		provider = new XenserverBackupTypeProvider(mockPlugin, mockContext)
	}

	def "getCode should return correct backup type code"() {
		when:
		String code = provider.getCode()

		then:
		code == 'xenSnapshot'
	}

	def "getName should return correct provider name"() {
		when:
		String name = provider.getName()

		then:
		name == "XCP-ng VM Snapshot"
	}

	def "getContainerType should return single"() {
		when:
		String containerType = provider.getContainerType()

		then:
		containerType == "single"
	}

	def "getCopyToStore should return true"() {
		when:
		Boolean copyToStore = provider.getCopyToStore()

		then:
		copyToStore == true
	}

	def "getDownloadEnabled should return true"() {
		when:
		Boolean downloadEnabled = provider.getDownloadEnabled()

		then:
		downloadEnabled == true
	}

	def "getRestoreExistingEnabled should return true"() {
		when:
		Boolean restoreExistingEnabled = provider.getRestoreExistingEnabled()

		then:
		restoreExistingEnabled == true
	}

	def "getRestoreNewEnabled should return true"() {
		when:
		Boolean restoreNewEnabled = provider.getRestoreNewEnabled()

		then:
		restoreNewEnabled == true
	}

	def "getRestoreType should return offline"() {
		when:
		String restoreType = provider.getRestoreType()

		then:
		restoreType == "offline"
	}

	def "isSnapshot should return true"() {
		when:
		Boolean isSnapshot = provider.isSnapshot()

		then:
		isSnapshot == true
	}

	def "getRestoreNewMode should return null"() {
		when:
		String restoreNewMode = provider.getRestoreNewMode()

		then:
		restoreNewMode == null
	}

	def "getHasCopyToStore should return true"() {
		when:
		Boolean hasCopyToStore = provider.getHasCopyToStore()

		then:
		hasCopyToStore == true
	}

	def "getOptionTypes should return empty list"() {
		when:
		Collection<OptionType> optionTypes = provider.getOptionTypes()

		then:
		optionTypes != null
		optionTypes instanceof ArrayList
		optionTypes.isEmpty()
	}

	def "getExecutionProvider should return XenserverBackupExecutionProvider instance"() {
		when:
		def executionProvider = provider.getExecutionProvider()

		then:
		executionProvider != null
		executionProvider instanceof XenserverBackupExecutionProvider
	}

	def "getExecutionProvider should return same instance on multiple calls"() {
		when:
		def executionProvider1 = provider.getExecutionProvider()
		def executionProvider2 = provider.getExecutionProvider()

		then:
		executionProvider1 != null
		executionProvider2 != null
		executionProvider1.is(executionProvider2)
	}

	def "getRestoreProvider should return XenserverBackupRestoreProvider instance"() {
		when:
		def restoreProvider = provider.getRestoreProvider()

		then:
		restoreProvider != null
		restoreProvider instanceof XenserverBackupRestoreProvider
	}

	def "getRestoreProvider should return same instance on multiple calls"() {
		when:
		def restoreProvider1 = provider.getRestoreProvider()
		def restoreProvider2 = provider.getRestoreProvider()

		then:
		restoreProvider1 != null
		restoreProvider2 != null
		restoreProvider1.is(restoreProvider2)
	}

	def "refresh should return success"() {
		given:
		Map authConfig = [host: "xenserver.local", username: "admin", password: "password"]
		BackupProviderModel backupProviderModel = new BackupProviderModel(id: 1L, name: "Test Backup Provider")

		when:
		ServiceResponse response = provider.refresh(authConfig, backupProviderModel)

		then:
		response != null
		response.success == true
	}

	def "refresh should handle null authConfig"() {
		given:
		Map authConfig = null
		BackupProviderModel backupProviderModel = new BackupProviderModel(id: 1L)

		when:
		ServiceResponse response = provider.refresh(authConfig, backupProviderModel)

		then:
		response != null
		response.success == true
	}

	def "refresh should handle null backupProviderModel"() {
		given:
		Map authConfig = [host: "xenserver.local"]
		BackupProviderModel backupProviderModel = null

		when:
		ServiceResponse response = provider.refresh(authConfig, backupProviderModel)

		then:
		response != null
		response.success == true
	}

	def "clean should return success"() {
		given:
		BackupProviderModel backupProviderModel = new BackupProviderModel(id: 1L, name: "Test Backup Provider")
		Map opts = [force: true]

		when:
		ServiceResponse response = provider.clean(backupProviderModel, opts)

		then:
		response != null
		response.success == true
	}

	def "clean should handle null opts"() {
		given:
		BackupProviderModel backupProviderModel = new BackupProviderModel(id: 1L)
		Map opts = null

		when:
		ServiceResponse response = provider.clean(backupProviderModel, opts)

		then:
		response != null
		response.success == true
	}

	def "clean should handle empty opts"() {
		given:
		BackupProviderModel backupProviderModel = new BackupProviderModel(id: 1L)
		Map opts = [:]

		when:
		ServiceResponse response = provider.clean(backupProviderModel, opts)

		then:
		response != null
		response.success == true
	}

	def "constructor should initialize plugin and morpheusContext"() {
		given:
		XenserverPlugin testPlugin = Mock(XenserverPlugin)
		MorpheusContext testContext = Mock(MorpheusContext)

		when:
		XenserverBackupTypeProvider testProvider = new XenserverBackupTypeProvider(testPlugin, testContext)

		then:
		testProvider.plugin == testPlugin
		testProvider.morpheus == testContext
	}

	def "getPlugin should return the plugin instance"() {
		when:
		def plugin = provider.getPlugin()

		then:
		plugin == mockPlugin
	}

	def "getMorpheus should return the morpheus context instance"() {
		when:
		def morpheus = provider.getMorpheus()

		then:
		morpheus == mockContext
	}
}

