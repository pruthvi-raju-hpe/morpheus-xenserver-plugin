package com.morpheusdata.xen.datasets

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.MorpheusAsyncServices
import com.morpheusdata.core.MorpheusServices
import com.morpheusdata.core.MorpheusVirtualImageService
import com.morpheusdata.core.data.DataQuery
import com.morpheusdata.core.data.DatasetInfo
import com.morpheusdata.core.data.DatasetQuery
import com.morpheusdata.core.synchronous.MorpheusSynchronousVirtualImageService
import com.morpheusdata.core.synchronous.cloud.MorpheusSynchronousCloudService
import com.morpheusdata.core.providers.ProvisionProvider
import com.morpheusdata.model.VirtualImage
import com.morpheusdata.model.VirtualImageType
import com.morpheusdata.model.Cloud
import com.morpheusdata.model.projection.VirtualImageIdentityProjection
import com.morpheusdata.xen.XenserverPlugin
import io.reactivex.rxjava3.core.Observable
import spock.lang.Specification
import spock.lang.Subject

class VirtualImageDatasetProviderSpec extends Specification {

    @Subject
    VirtualImageDatasetProvider provider

    XenserverPlugin mockPlugin
    MorpheusContext mockContext
    MorpheusSynchronousVirtualImageService mockSyncVirtualImageService
    MorpheusVirtualImageService mockAsyncVirtualImageService
    MorpheusSynchronousCloudService mockCloudService

    def setup() {
        mockPlugin = Mock(XenserverPlugin)
        mockContext = Mock(MorpheusContext)

        mockSyncVirtualImageService = Mock(MorpheusSynchronousVirtualImageService)
        mockAsyncVirtualImageService = Mock(MorpheusVirtualImageService)
        mockCloudService = Mock(MorpheusSynchronousCloudService)

        def mockServices = Mock(MorpheusServices)
        def mockAsync = Mock(MorpheusAsyncServices)

        mockServices.virtualImage >> mockSyncVirtualImageService
        mockServices.cloud >> mockCloudService
        mockAsync.virtualImage >> mockAsyncVirtualImageService

        mockContext.services >> mockServices
        mockContext.async >> mockAsync

        provider = new VirtualImageDatasetProvider(mockPlugin, mockContext)
    }

    def "getInfo should return correct dataset information"() {
        when:
        DatasetInfo info = provider.getInfo()

        then:
        info != null
        info.name == VirtualImageDatasetProvider.providerName
        info.namespace == VirtualImageDatasetProvider.providerNamespace
        info.key == VirtualImageDatasetProvider.providerKey
        info.description == VirtualImageDatasetProvider.providerDescription
    }

    def "getInfo should return expected values"() {
        when:
        DatasetInfo info = provider.getInfo()

        then:
        info.name == 'XCP-ng Virtual Images'
        info.namespace == 'xcpng'
        info.key == 'xcpVirtualImages'
        info.description == 'Get virtual images for XCP-ng provisioning.'
    }

    def "getItemType should return VirtualImage class"() {
        when:
        Class itemType = provider.getItemType()

        then:
        itemType == VirtualImage.class
    }

    def "list should return observable of virtual images"() {
        given:
        DatasetQuery datasetQuery = new DatasetQuery()
        VirtualImage image1 = new VirtualImage(id: 1L, name: "image1")
        VirtualImage image2 = new VirtualImage(id: 2L, name: "image2")

        mockAsyncVirtualImageService.list(_ as DataQuery) >> Observable.fromIterable([image1, image2])

        when:
        Observable<VirtualImage> result = provider.list(datasetQuery)
        List<VirtualImage> images = result.toList().blockingGet()

        then:
        images.size() == 2
        images[0].id == 1L
        images[1].id == 2L
    }

    def "listOptions should return mapped name-value pairs"() {
        given:
        DatasetQuery datasetQuery = new DatasetQuery()
        VirtualImageIdentityProjection proj1 = new VirtualImageIdentityProjection(id: 1L, name: "Image 1")
        VirtualImageIdentityProjection proj2 = new VirtualImageIdentityProjection(id: 2L, name: "Image 2")

        mockAsyncVirtualImageService.listIdentityProjections(_ as DataQuery) >>
            Observable.fromIterable([proj1, proj2])

        when:
        Observable<Map> result = provider.listOptions(datasetQuery)
        List<Map> options = result.toList().blockingGet()

        then:
        options.size() == 2
        options[0].name == "Image 1"
        options[0].value == 1L
        options[1].name == "Image 2"
        options[1].value == 2L
    }

    def "fetchItem should fetch item by Long value"() {
        given:
        Long itemId = 123L
        VirtualImage expectedImage = new VirtualImage(id: itemId, name: "test-image")
        mockSyncVirtualImageService.get(itemId) >> expectedImage

        when:
        VirtualImage result = provider.fetchItem(itemId)

        then:
        result == expectedImage
        result.id == itemId
    }

    def "fetchItem should fetch item by String value"() {
        given:
        String itemId = "456"
        VirtualImage expectedImage = new VirtualImage(id: 456L, name: "test-image")
        mockSyncVirtualImageService.get(456L) >> expectedImage

        when:
        VirtualImage result = provider.fetchItem(itemId)

        then:
        result == expectedImage
        result.id == 456L
    }

    def "fetchItem should return null for invalid String value"() {
        given:
        String itemId = "invalid"

        when:
        VirtualImage result = provider.fetchItem(itemId)

        then:
        result == null
    }

    def "fetchItem should return null for null value"() {
        when:
        VirtualImage result = provider.fetchItem(null)

        then:
        result == null
    }

    def "item should fetch virtual image by id"() {
        given:
        Long itemId = 789L
        VirtualImage expectedImage = new VirtualImage(id: itemId, name: "item-image")
        mockSyncVirtualImageService.get(itemId) >> expectedImage

        when:
        VirtualImage result = provider.item(itemId)

        then:
        result == expectedImage
        result.id == itemId
    }

    def "constructor should initialize plugin and morpheusContext"() {
        given:
        XenserverPlugin testPlugin = Mock(XenserverPlugin)
        MorpheusContext testContext = Mock(MorpheusContext)

        when:
        VirtualImageDatasetProvider testProvider = new VirtualImageDatasetProvider(testPlugin, testContext)

        then:
        testProvider.plugin == testPlugin
        testProvider.morpheusContext == testContext
    }

    def "provider constants should have expected values"() {
        expect:
        VirtualImageDatasetProvider.providerName == 'XCP-ng Virtual Images'
        VirtualImageDatasetProvider.providerNamespace == 'xcpng'
        VirtualImageDatasetProvider.providerKey == 'xcpVirtualImages'
        VirtualImageDatasetProvider.providerDescription == 'Get virtual images for XCP-ng provisioning.'
    }

    def "list should handle empty results"() {
        given:
        DatasetQuery datasetQuery = new DatasetQuery()
        mockAsyncVirtualImageService.list(_ as DataQuery) >> Observable.empty()

        when:
        Observable<VirtualImage> result = provider.list(datasetQuery)
        List<VirtualImage> images = result.toList().blockingGet()

        then:
        images.isEmpty()
    }

    def "listOptions should handle empty results"() {
        given:
        DatasetQuery datasetQuery = new DatasetQuery()
        mockAsyncVirtualImageService.listIdentityProjections(_ as DataQuery) >> Observable.empty()

        when:
        Observable<Map> result = provider.listOptions(datasetQuery)
        List<Map> options = result.toList().blockingGet()

        then:
        options.isEmpty()
    }

    def "itemName should return virtual image name"() {
        given:
        VirtualImage image = new VirtualImage(id: 1L, name: "test-image-name")

        when:
        String result = provider.itemName(image)

        then:
        result == "test-image-name"
    }

    def "itemValue should return virtual image id"() {
        given:
        VirtualImage image = new VirtualImage(id: 12345L, name: "test-image")

        when:
        Long result = provider.itemValue(image)

        then:
        result == 12345L
    }

    def "getImageTypes should return unique image type codes from provision providers"() {
        given:
        def mockProvisionProvider1 = Mock(ProvisionProvider)
        def mockProvisionProvider2 = Mock(ProvisionProvider)

        def imageType1 = new VirtualImageType(code: "xen.image")
        def imageType2 = new VirtualImageType(code: "xen.iso")
        def imageType3 = new VirtualImageType(code: "xen.image") // duplicate

        mockProvisionProvider1.getVirtualImageTypes() >> [imageType1, imageType2]
        mockProvisionProvider2.getVirtualImageTypes() >> [imageType3]

        mockPlugin.getProvidersByType(ProvisionProvider) >> [mockProvisionProvider1, mockProvisionProvider2]

        when:
        List<String> result = provider.getImageTypes()

        then:
        result.size() == 2
        result.contains("xen.image")
        result.contains("xen.iso")
    }

    def "getImageTypes should handle empty providers"() {
        given:
        mockPlugin.getProvidersByType(ProvisionProvider) >> []

        when:
        List<String> result = provider.getImageTypes()

        then:
        result.isEmpty()
    }

    def "buildQuery should build query without zoneId"() {
        given:
        DatasetQuery datasetQuery = new DatasetQuery()
        datasetQuery.parameters = [accountId: "100"]

        def imageType1 = new VirtualImageType(code: "xen.image")
        def mockProvisionProvider = Mock(ProvisionProvider)
        mockProvisionProvider.getVirtualImageTypes() >> [imageType1]
        mockPlugin.getProvidersByType(ProvisionProvider) >> [mockProvisionProvider]

        when:
        DataQuery result = provider.buildQuery(datasetQuery)

        then:
        result != null
        result.sort == "name"
        result.order == DataQuery.SortOrder.asc
    }

    def "buildQuery should build query with zoneId"() {
        given:
        DatasetQuery datasetQuery = new DatasetQuery()
        datasetQuery.parameters = [accountId: "100", zoneId: "50"]

        Cloud mockCloud = new Cloud(id: 50L, name: "test-cloud")
        mockCloudService.get(50L) >> mockCloud

        def imageType1 = new VirtualImageType(code: "xen.image")
        def mockProvisionProvider = Mock(ProvisionProvider)
        mockProvisionProvider.getVirtualImageTypes() >> [imageType1]
        mockPlugin.getProvidersByType(ProvisionProvider) >> [mockProvisionProvider]

        when:
        DataQuery result = provider.buildQuery(datasetQuery)

        then:
        result != null
        result.sort == "name"
        result.order == DataQuery.SortOrder.asc
    }

    def "buildQuery should handle null zoneId"() {
        given:
        DatasetQuery datasetQuery = new DatasetQuery()
        datasetQuery.parameters = [accountId: "100", zoneId: null]

        def imageType1 = new VirtualImageType(code: "xen.image")
        def mockProvisionProvider = Mock(ProvisionProvider)
        mockProvisionProvider.getVirtualImageTypes() >> [imageType1]
        mockPlugin.getProvidersByType(ProvisionProvider) >> [mockProvisionProvider]

        when:
        DataQuery result = provider.buildQuery(datasetQuery)

        then:
        result != null
    }

    def "buildQuery should handle invalid zoneId"() {
        given:
        DatasetQuery datasetQuery = new DatasetQuery()
        datasetQuery.parameters = [accountId: "100", zoneId: "invalid"]

        def imageType1 = new VirtualImageType(code: "xen.image")
        def mockProvisionProvider = Mock(ProvisionProvider)
        mockProvisionProvider.getVirtualImageTypes() >> [imageType1]
        mockPlugin.getProvidersByType(ProvisionProvider) >> [mockProvisionProvider]

        when:
        DataQuery result = provider.buildQuery(datasetQuery)

        then:
        thrown(NumberFormatException)
    }

    def "buildQuery should handle multiple image types"() {
        given:
        DatasetQuery datasetQuery = new DatasetQuery()
        datasetQuery.parameters = [:]

        def imageType1 = new VirtualImageType(code: "xen.image")
        def imageType2 = new VirtualImageType(code: "xen.iso")
        def imageType3 = new VirtualImageType(code: "xen.template")

        def mockProvisionProvider1 = Mock(ProvisionProvider)
        def mockProvisionProvider2 = Mock(ProvisionProvider)

        mockProvisionProvider1.getVirtualImageTypes() >> [imageType1, imageType2]
        mockProvisionProvider2.getVirtualImageTypes() >> [imageType3]

        mockPlugin.getProvidersByType(ProvisionProvider) >> [mockProvisionProvider1, mockProvisionProvider2]

        when:
        DataQuery result = provider.buildQuery(datasetQuery)

        then:
        result != null
    }
}
