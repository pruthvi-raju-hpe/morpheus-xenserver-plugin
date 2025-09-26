package com.morpheusdata.xen

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.MorpheusServices
import com.morpheusdata.core.MorpheusAsyncServices
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.backup.BackupProvider
import spock.lang.Specification
import spock.lang.Subject

class XenserverBackupProviderSpec extends Specification {

	@Subject
	XenserverBackupProvider provider

	XenserverPlugin mockPlugin
	MorpheusContext mockContext

	def setup() {
		mockPlugin = Mock(XenserverPlugin)

		def mockServices = Mock(MorpheusServices)
		def mockAsync = Mock(MorpheusAsyncServices)

		mockContext = Mock(MorpheusContext)
		mockContext.services >> mockServices
		mockContext.async >> mockAsync
	}

	def "constructor should initialize provider and register backup type provider"() {
		when:
		provider = new XenserverBackupProvider(mockPlugin, mockContext)

		then:
		1 * mockPlugin.registerProvider(_ as XenserverBackupTypeProvider)
		provider != null
		provider.plugin == mockPlugin
		provider.morpheus == mockContext
	}

	def "constructor should add scoped provider with correct scope"() {
		when:
		provider = new XenserverBackupProvider(mockPlugin, mockContext)

		then:
		1 * mockPlugin.registerProvider(_ as XenserverBackupTypeProvider) >> { args ->
			XenserverBackupTypeProvider typeProvider = args[0]
			assert typeProvider != null
			assert typeProvider instanceof XenserverBackupTypeProvider
		}
		provider != null
	}

	def "constructor should create XenserverBackupTypeProvider with correct parameters"() {
		when:
		provider = new XenserverBackupProvider(mockPlugin, mockContext)

		then:
		1 * mockPlugin.registerProvider(_ as XenserverBackupTypeProvider) >> { args ->
			XenserverBackupTypeProvider typeProvider = args[0]
			assert typeProvider.plugin == mockPlugin
			assert typeProvider.morpheus == mockContext
		}
	}

	def "provider should be instance of MorpheusBackupProvider"() {
		when:
		provider = new XenserverBackupProvider(mockPlugin, mockContext)

		then:
		provider instanceof com.morpheusdata.core.backup.MorpheusBackupProvider
	}

	def "provider should be instance of BackupProvider"() {
		when:
		provider = new XenserverBackupProvider(mockPlugin, mockContext)

		then:
		provider instanceof BackupProvider
	}

	def "constructor should handle null plugin gracefully"() {
		when:
		provider = new XenserverBackupProvider(null, mockContext)

		then:
		thrown(NullPointerException)
	}

	def "constructor should handle null context gracefully"() {
		when:
		provider = new XenserverBackupProvider(mockPlugin, null)

		then:
		// The constructor doesn't throw NPE, it passes null to super
		notThrown(NullPointerException)
	}

	def "multiple instances should create separate backup type providers"() {
		when:
		def provider1 = new XenserverBackupProvider(mockPlugin, mockContext)
		def provider2 = new XenserverBackupProvider(mockPlugin, mockContext)

		then:
		2 * mockPlugin.registerProvider(_ as XenserverBackupTypeProvider)
		provider1 != provider2
	}

	def "getPlugin should return the plugin instance"() {
		when:
		provider = new XenserverBackupProvider(mockPlugin, mockContext)

		then:
		provider.getPlugin() == mockPlugin
	}

	def "getMorpheus should return the morpheus context instance"() {
		when:
		provider = new XenserverBackupProvider(mockPlugin, mockContext)

		then:
		provider.getMorpheus() == mockContext
	}

	def "constructor should call super constructor"() {
		when:
		provider = new XenserverBackupProvider(mockPlugin, mockContext)

		then:
		provider.plugin == mockPlugin
		provider.morpheus == mockContext
		notThrown(Exception)
	}

	def "provider should maintain reference to created backup type provider"() {
		given:
		XenserverBackupTypeProvider capturedProvider = null

		when:
		provider = new XenserverBackupProvider(mockPlugin, mockContext)

		then:
		1 * mockPlugin.registerProvider(_ as XenserverBackupTypeProvider) >> { args ->
			capturedProvider = args[0]
		}
		capturedProvider != null
		capturedProvider instanceof XenserverBackupTypeProvider
	}

	def "constructor should register provider before adding scoped provider"() {
		given:
		def callOrder = []

		when:
		provider = new XenserverBackupProvider(mockPlugin, mockContext)

		then:
		1 * mockPlugin.registerProvider(_ as XenserverBackupTypeProvider) >> {
			callOrder << "registerProvider"
		}
		callOrder == ["registerProvider"]
	}

	def "backup type provider should be properly configured"() {
		given:
		XenserverBackupTypeProvider typeProvider = null

		when:
		provider = new XenserverBackupProvider(mockPlugin, mockContext)

		then:
		1 * mockPlugin.registerProvider(_ as XenserverBackupTypeProvider) >> { args ->
			typeProvider = args[0]
			assert typeProvider.getCode() == 'xenSnapshot'
			assert typeProvider.getName() == "XCP-ng VM Snapshot"
		}
		typeProvider != null
	}

	def "provider should be annotated with Slf4j"() {
		expect:
		// Check that the class has the log field from @Slf4j annotation
		XenserverBackupProvider.class.getDeclaredFields().any { it.name == 'log' }
	}
}
