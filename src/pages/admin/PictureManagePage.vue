<template>
  <div id="pictureManagePage">
    <a-flex justify="space-between">
      <h2>图片管理</h2>
      <a-space>
        <a-button type="primary" href="/add_picture" target="_blank">+ 创建图片</a-button>
        <a-button type="primary" href="/add_picture/batch" target="_blank" ghost>+ 批量创建图片</a-button>
        <a-button type="primary" @click="openAllPictures" ghost>全部开放</a-button>
      </a-space>
    </a-flex>
    <div style="margin-bottom: 16px" />
    <!-- 搜索表单 -->
    <a-form layout="inline" :model="searchParams" @finish="doSearch">
      <a-form-item label="关键词">
        <a-input
          v-model:value="searchParams.searchText"
          placeholder="从名称和简介搜索"
          allow-clear
        />
      </a-form-item>
      <a-form-item label="类型">
        <a-input v-model:value="searchParams.category" placeholder="请输入类型" allow-clear />
      </a-form-item>
      <a-form-item label="标签">
        <a-select
          v-model:value="searchParams.tags"
          mode="tags"
          placeholder="请输入标签"
          style="min-width: 180px"
          allow-clear
        />
      </a-form-item>
      <a-form-item name="reviewStatus" label="审核状态">
        <a-select
          v-model:value="searchParams.reviewStatus"
          style="min-width: 180px"
          placeholder="请选择审核状态"
          :options="PIC_REVIEW_STATUS_OPTIONS"
          allow-clear
        />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" html-type="submit">搜索</a-button>
      </a-form-item>
    </a-form>
    <div style="margin-bottom: 16px" />

    <!-- 表格 -->
    <a-table
      :columns="columns"
      :data-source="dataList"
      :pagination="pagination"
      @change="doTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'url'">
          <a-image :src="record.url" :width="60" />
        </template>
        <template v-if="column.dataIndex === 'tags'">
          <a-space wrap>
            <a-tag v-for="tag in JSON.parse(record.tags || '[]')" :key="tag">
              {{ tag }}
            </a-tag>
          </a-space>
        </template>
        <template v-if="column.dataIndex === 'picInfo'">
          <div>格式：{{ record.picFormat }}</div>
<!--          <div>宽度：{{ record.picWidth }}</div>-->
<!--          <div>高度：{{ record.picHeight }}</div>-->
<!--          <div>宽高比：{{ record.picScale }}</div>-->
          <div>大小：{{ (record.picSize / 1024).toFixed(2) }}KB</div>
        </template>
        <template v-if="column.dataIndex === 'reviewMessage'">
          <div>审核状态：{{ PIC_REVIEW_STATUS_MAP[record.reviewStatus] }}</div>
<!--          <div>审核信息：{{ record.reviewMessage }}</div>-->
<!--          <div>审核人：{{ record.reviewerId }}</div>-->
<!--          <div v-if="record.reviewTime">-->
<!--            审核时间：{{ dayjs(record.reviewTime).format('YYYY-MM-DD HH:mm:ss') }}-->
<!--          </div>-->
        </template>
        <template v-if="column.dataIndex === 'createTime'">
          {{ dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss') }}
        </template>
        <template v-if="column.dataIndex === 'editTime'">
          {{ dayjs(record.editTime).format('YYYY-MM-DD HH:mm:ss') }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space wrap>
            <a-button
              type="link"
              @click="handleReview(record, PIC_REVIEW_STATUS_ENUM.PASS)"
              :disabled="record.reviewStatus === PIC_REVIEW_STATUS_ENUM.PASS || record.reviewStatus === PIC_REVIEW_STATUS_ENUM.REVIEWEDWAITTOPASS"
              :style="record.reviewStatus === PIC_REVIEW_STATUS_ENUM.REVIEWEDWAITTOPASS ? { color: 'skyblue' } : {}"
            >
              {{
                record.reviewStatus === PIC_REVIEW_STATUS_ENUM.PASS ? '已通过' :
                  record.reviewStatus === PIC_REVIEW_STATUS_ENUM.REVIEWEDWAITTOPASS ? '已开放' : '通过'
              }}
            </a-button>
            <a-button
              type="link"
              danger
              @click="handleReview(record, PIC_REVIEW_STATUS_ENUM.REJECT)"
              :disabled="record.reviewStatus === PIC_REVIEW_STATUS_ENUM.REJECT"
            >
              拒绝
            </a-button>
            <a-button type="link" :href="`/add_picture?id=${record.id}&current=${searchParams.current}`" target="_self">编辑</a-button>
            <a-button type="link" danger @click="doDelete(record.id, record.current)">删除</a-button>
          </a-space>
        </template>
      </template>
    </a-table>
  </div>
</template>
<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  deletePictureUsingPost,
  doPictureReviewUsingPost,
  listPictureByPageUsingPost,
  openPictureUsingPost
} from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import {
  PIC_REVIEW_STATUS_ENUM,
  PIC_REVIEW_STATUS_MAP,
  PIC_REVIEW_STATUS_OPTIONS,
} from '../../constants/picture.ts'
import dayjs from 'dayjs'

// 全部开放图片
const openAllPictures = async () => {
  const res = await openPictureUsingPost()
  if (res.data.code === 0) {
    message.success('全部开放成功')
    fetchData()
  } else {
    message.error('全部开放失败，' + res.data.message)
  }
}

const columns = [
  {
    title: 'id',
    dataIndex: 'id',
    width: 80,
  },
  {
    title: '图片',
    dataIndex: 'url',
  },
  {
    title: '名称',
    dataIndex: 'name',
  },
  {
    title: '简介',
    dataIndex: 'introduction',
    ellipsis: true,
  },
  {
    title: '类型',
    dataIndex: 'category',
  },
  {
    title: '标签',
    dataIndex: 'tags',
  },
  {
    title: '图片信息',
    dataIndex: 'picInfo',
  },
  {
    title: '用户 id',
    dataIndex: 'userId',
    width: 80,
  },
  {
    title: '空间 id',
    dataIndex: 'spaceId',
    width: 80,
  },
  {
    title: '审核信息',
    dataIndex: 'reviewMessage',
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
  },
  {
    title: '编辑时间',
    dataIndex: 'editTime',
  },
  {
    title: '操作',
    key: 'action',
  },
]

// 定义数据
const dataList = ref<API.Picture[]>([])
const total = ref(0)

// 搜索条件
const searchParams = reactive<API.PictureQueryRequest>({
  current: 1,
  pageSize: 10,
  sortField: 'createTime',
  sortOrder: 'descend',
})

// 获取数据
const fetchData = async () => {
  const res = await listPictureByPageUsingPost({
    ...searchParams,
    nullSpaceId: true,
  })
  if (res.data.code === 0 && res.data.data) {
    dataList.value = res.data.data.records ?? []
    total.value = res.data.data.total ?? 0
  } else {
    message.error('获取数据失败，' + res.data.message)
  }
}

// 页面加载时获取数据，请求一次
onMounted(() => {
  fetchData()
})

// 分页参数
const pagination = computed(() => {
  return {
    current: searchParams.current,
    pageSize: searchParams.pageSize,
    total: total.value,
    showSizeChanger: true,
    showTotal: (total) => `共 ${total} 条`,
  }
})

// 表格变化之后，重新获取数据
const doTableChange = (page: any) => {
  searchParams.current = page.current
  searchParams.pageSize = page.pageSize
  fetchData()
}

// 搜索数据
const doSearch = () => {
  // 重置页码
  searchParams.current = 1
  fetchData()
}

// 删除数据
const doDelete = async (id: string, current: number) => {
  if (!id) {
    return
  }
  const res = await deletePictureUsingPost({
    id,
    current: searchParams.current
  })
  if (res.data.code === 0) {
    message.success('删除成功')
    // 刷新数据
    fetchData()
  } else {
    message.error('删除失败')
  }
}

// 审核图片
const handleReview = async (record: API.Picture, reviewStatus: number) => {
  const reviewMessage =
    reviewStatus === PIC_REVIEW_STATUS_ENUM.PASS ? '管理员操作通过' : '管理员操作拒绝'
  const res = await doPictureReviewUsingPost({
    id: record.id,
    reviewStatus,
    reviewMessage,
  })
  if (res.data.code === 0) {
    message.success('审核操作成功')
    // 重新获取列表数据
    fetchData()
  } else {
    message.error('审核操作失败，' + res.data.message)
  }
}
</script>






<!--<template>
  <div id="pictureManagePage" style="padding: 20px;">
    <a-row type="flex" justify="space-between" align="middle">
      <h2 style="margin-bottom: 20px;">图片管理</h2>
      <a-space size="large">
        <a-button type="primary" href="/add_picture" target="_blank">+ 创建图片</a-button>
        <a-button type="dashed" href="/add_picture/batch" target="_blank">+ 批量创建图片</a-button>
        <a-button type="primary" @click="openAllPictures" ghost>全部开放</a-button>
      </a-space>
    </a-row>

    &lt;!&ndash; 搜索表单 &ndash;&gt;
    <a-form layout="inline" :model="searchParams" @finish="doSearch" style="margin-bottom: 20px;">
      <a-form-item label="关键词">
        <a-input v-model:value="searchParams.searchText" placeholder="从名称和简介搜索" allow-clear />
      </a-form-item>
      <a-form-item label="类型">
        <a-input v-model:value="searchParams.category" placeholder="请输入类型" allow-clear />
      </a-form-item>
      <a-form-item label="标签">
        <a-select v-model:value="searchParams.tags" mode="tags" placeholder="请输入标签" style="min-width: 180px;" allow-clear />
      </a-form-item>
      <a-form-item name="reviewStatus" label="审核状态">
        <a-select v-model:value="searchParams.reviewStatus" style="min-width: 180px;" placeholder="请选择审核状态" :options="PIC_REVIEW_STATUS_OPTIONS" allow-clear />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" html-type="submit">搜索</a-button>
      </a-form-item>
    </a-form>

    &lt;!&ndash; 卡片式表格 &ndash;&gt;
    <a-row :gutter="[16, 16]">
      <a-col :span="8" v-for="record in dataList" :key="record.id">
        <a-card hoverable style="height: 100%;">
          <template #cover>
            <a-image :src="record.url" :width="300" style="height: 200px; object-fit: cover;" preview />
          </template>
          <a-card-meta :title="record.name">
            <template #description>
              <p>简介: {{ record.introduction }}</p>
              <p>类型：{{ record.category }}</p>
              <p>标签：<a-tag v-for="tag in JSON.parse(record.tags || '[]')" :key="tag">{{ tag }}</a-tag></p>
              <p>格式：{{ record.picFormat }} | 宽度：{{ record.picWidth }} | 高度：{{ record.picHeight }}</p>
              <p>大小：{{ (record.picSize / 1024).toFixed(2) }}KB</p>
              <p>审核状态：{{ PIC_REVIEW_STATUS_MAP[record.reviewStatus] }}</p>
              <p v-if="record.reviewMessage">审核信息：{{ record.reviewMessage }}</p>
              <p>创建时间：{{ dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss') }}</p>
            </template>
          </a-card-meta>

          <div class="card-actions">
            <a-button
              type="link"
              @click="handleReview(record, PIC_REVIEW_STATUS_ENUM.PASS)"
              :disabled="record.reviewStatus === PIC_REVIEW_STATUS_ENUM.PASS || record.reviewStatus === PIC_REVIEW_STATUS_ENUM.REVIEWEDWAITTOPASS"
              :style="record.reviewStatus === PIC_REVIEW_STATUS_ENUM.REVIEWEDWAITTOPASS ? { color: 'skyblue' } : {}"
            >
              {{
                record.reviewStatus === PIC_REVIEW_STATUS_ENUM.PASS ? '已通过' :
                  record.reviewStatus === PIC_REVIEW_STATUS_ENUM.REVIEWEDWAITTOPASS ? '已开放' : '通过'
              }}
            </a-button>
            <a-button
              type="link"
              danger
              @click="handleReview(record, PIC_REVIEW_STATUS_ENUM.REJECT)"
              :disabled="record.reviewStatus === PIC_REVIEW_STATUS_ENUM.REJECT"
            >
              拒绝
            </a-button>
            <a-button type="link" :href="`/add_picture?id=${record.id}`" target="_blank">编辑</a-button>
            <a-button type="link" danger @click="doDelete(record.id)">删除</a-button>
          </div>
        </a-card>
      </a-col>
    </a-row>

    &lt;!&ndash; 分页 &ndash;&gt;
    <a-pagination
      :current="pagination.current"
      :pageSize="pagination.pageSize"
      :total="pagination.total"
      :showSizeChanger="pagination.showSizeChanger"
      :showTotal="total => pagination.showTotal(total)"
      @change="doTableChange"
      @showSizeChange="(current, pageSize) => { searchParams.pageSize = pageSize; doTableChange({ current }) }"
    />
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  deletePictureUsingPost,
  doPictureReviewUsingPost,
  listPictureByPageUsingPost,
  openPictureUsingPost
} from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import {
  PIC_REVIEW_STATUS_ENUM,
  PIC_REVIEW_STATUS_MAP,
  PIC_REVIEW_STATUS_OPTIONS,
} from '../../constants/picture.ts'
import dayjs from 'dayjs'

// 全部开放图片
const openAllPictures = async () => {
  const res = await openPictureUsingPost()
  if (res.data.code === 0) {
    message.success('全部开放成功')
    fetchData()
  } else {
    message.error('全部开放失败，' + res.data.message)
  }
}

// 数据列表和分页
const dataList = ref([])
const total = ref(0)

// 搜索条件
const searchParams = reactive({
  current: 1,
  pageSize: 9, // 建议设置为3的倍数，方便卡片布局
  sortField: 'createTime',
  sortOrder: 'descend',
})

// 获取数据函数
const fetchData = async () => {
  const res = await listPictureByPageUsingPost({ ...searchParams, nullSpaceId: true })
  if (res.data.code === 0 && res.data.data) {
    dataList.value = res.data.data.records ?? []
    total.value = res.data.data.total ?? 0
  } else {
    message.error('获取数据失败，' + res.data.message)
  }
}

onMounted(() => {
  fetchData()
})

// 分页配置
const pagination = computed(() => ({
  current: searchParams.current,
  pageSize: searchParams.pageSize,
  total: total.value,
  showSizeChanger: true,
  showTotal: (total) => `共 ${total} 条`,
  pageSizeOptions: ['9', '18', '27'],
}))

// 表格变化处理
const doTableChange = (paginator: any) => {
  searchParams.current = paginator.current
  searchParams.pageSize = paginator.pageSize
  fetchData()
}

// 搜索处理
const doSearch = () => {
  searchParams.current = 1
  fetchData()
}

// 删除处理
const doDelete = async (id: string) => {
  if (!id) return
  const res = await deletePictureUsingPost({ id })
  if (res.data.code === 0) {
    message.success('删除成功')
    fetchData()
  } else {
    message.error('删除失败')
  }
}

// 审核处理
const handleReview = async (record: any, reviewStatus: number) => {
  const reviewMessage = reviewStatus === PIC_REVIEW_STATUS_ENUM.PASS ? '管理员操作通过' : '管理员操作拒绝'
  const res = await doPictureReviewUsingPost({ id: record.id, reviewStatus, reviewMessage })
  if (res.data.code === 0) {
    message.success('审核操作成功')
    fetchData()
  } else {
    message.error('审核操作失败，' + res.data.message)
  }
}
</script>

<style scoped>
#pictureManagePage {
  background-color: #f0f2f5;
}

.card-actions {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
}

.card-actions > * {
  flex: 1;
  text-align: center;
}

/* 自定义表格样式 */
:deep(.ant-table) {
  background: transparent;
}

:deep(.ant-table-tbody) > tr > td {
  padding: 8px !important;
  border-bottom: none !important;
}

:deep(.ant-pagination) {
  margin-top: 24px;
}
</style>-->

