<template>
  <div class="portal-page">
    <main class="portal-main">
      <section class="hero">
        <div class="brand-block">
          <h1 class="brand-name">FOLIO</h1>
          <p class="brand-tagline">轻量 · 智能 · 多人协作的学习笔记交流空间</p>
        </div>

        <h2 class="hero-title">专注知识沉淀，让笔记真正“被看见”</h2>

        <div class="hero-actions hero-actions-center">
          <router-link to="/register" class="primary-btn primary-btn-large">
            注册
          </router-link>
          <router-link to="/login" class="primary-btn primary-btn-large">
            登录
          </router-link>
        </div>

        <p class="hero-helper hero-helper-center">
          即刻加入我们，帮助你高效搭建个人知识库。
        </p>

        <div class="hero-description">
          <p>
            在信息爆炸的时代，真正有价值的知识，往往散落在每个人的笔记里。知识分享笔记社区FOLIO，正是为此而生。
          </p>
          <p>  </p>
          <p>
            我们希望搭建一个干净、高质量、以学习为核心的知识交流空间，让每一篇认真书写的笔记，都能被看见、被收藏、被讨论，也能反过来点亮更多人的思考。
          </p>
          <p>  </p>
          <p>
            在这里，你可以发布和整理自己的学习笔记、读书摘录、课程总结和实践心得；也可以通过搜索与推荐，快速发现他人优质内容，构建属于自己的知识地图。
          </p>
          <p>  </p>
          <p>
            我们提供评论区、问答圈子、私信与关注功能，让提问、解答与交流变得自然顺畅，让同频的人更容易相遇。
          </p>
          <p>  </p>
          <p>
            同时，我们针对不当内容设置了审核与敏感词检测机制，配合后台管理与用户举报通道，努力营造一个安全、友好、无水贴的学习社区环境。
          </p>
          <p>  </p>
          <p>
            无论你是正在备考的学生、职场进阶的新人，还是乐于分享的内容创作者，都能在这里找到属于自己的位置
          </p>
          <p>  </p>
          <p>
            让认真做笔记的人，不再是一个人战斗；让知识的价值，在分享与交流中持续放大。欢迎加入我们，一起把每一次记录，变成可以被世界看见的成长足迹。
          </p>
          <p>  </p>
          <p>
            无论你是整理课堂笔记、准备考试复习，还是沉淀科研灵感与项目记录，都可以把 FOLIO 当作一个长期演进的个人知识档案库。
          </p>
          <p>  </p>
          <p>
            通过与同学的互动交流，让“一个人的笔记本”自然生长为“一个班级、一个学院共享的知识网络”。
          </p>
        </div>

        <div class="hero-showcase">
          <div class="showcase-title">功能演示</div>
          <div class="showcase-track">
            <div
              class="showcase-track-inner"
              :style="{ '--current-index': currentIndex }"
            >
              <!-- 一次性加载四张图片，保证切换时不再重新加载 -->
              <img
                v-for="img in showcaseImages"
                :key="img.src"
                class="showcase-image"
                :src="img.src"
                :alt="img.alt"
              />
            </div>
          </div>
          <div class="showcase-dots">
            <button
              v-for="(img, idx) in showcaseImages"
              :key="img.src"
              class="dot"
              :class="{ active: idx === currentIndex }"
              type="button"
              @click="currentIndex = idx"
            />
          </div>
        </div>

      </section>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'

const showcaseImages = [
  {
    src: '/assets/portal/Trendingtoday.png',
    alt: '今日热点功能演示'
  },
  {
    src: '/assets/portal/Dailyrecommendation.png',
    alt: '每日推荐功能演示'
  },
  {
    src: '/assets/portal/Personalnotes.png',
    alt: '个人笔记空间演示'
  },
  {
    src: '/assets/portal/QAexample.png',
    alt: '问答讨论功能演示'
  }
]

const currentIndex = ref(0)
let timer = null

onMounted(() => {
  timer = setInterval(() => {
    currentIndex.value = (currentIndex.value + 1) % showcaseImages.length
  }, 3500)
})

onUnmounted(() => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
})
</script>

<style scoped>
.portal-page {
  min-height: 100vh;
  padding: 40px 24px 56px;
  background: radial-gradient(circle at top left, #ecfdf5 0, #ffffff 45%, #f0fdf4 100%);
  color: #0f172a;
  font-family: "Microsoft YaHei", "微软雅黑", "PingFang SC", system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.portal-header {
  display: none;
}

.brand-block {
  text-align: center;
  margin-bottom: 24px;
}

.brand-name {
  font-size: 54px;
  font-weight: 700;
  letter-spacing: 0.18em;
  color: #022c22;
}

.brand-tagline {
  font-size: 19px;
  color: #6b7280;
  margin-top: 6px;
}

.portal-main {
  width: 100%;
  max-width: 1500px;
  display: flex;
  justify-content: center;
}

.hero {
  width: 100%;
  padding: 24px 0 0;
}

.hero-title {
  font-size: 40px;
  line-height: 2;
  font-weight: 650;
  letter-spacing: -0.03em;
  color: #022c22;
  margin-bottom: 24px;
  text-align: center;
}

.hero-subtitle {
  font-size: 19px;
  line-height: 5;
  color: #475569;
  max-width: 1400px;
  margin: 20px auto 28px;
  text-align: center;
}

.hero-description {
  margin: 56px 0 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  font-size: 20px;
  line-height: 3.5;
  color: black;
  font-weight: 500;
  font-family: "Microsoft YaHei", "微软雅黑", "PingFang SC", system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
}

.hero-description p {
  text-indent: 2em; /* 每段开头空出两个汉字宽度 */
}

.hero-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 24px 0 12px;
}

.hero-actions-center {
  justify-content: center;
}

.primary-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 26px;
  height: 42px;
  border-radius: 999px;
  border: none;
  outline: none;
  cursor: pointer;
  background: linear-gradient(135deg, #22c55e, #16a34a);
  color: #f9fafb;
  font-size: 19px;
  font-weight: 600;
  letter-spacing: 0.03em;
  text-decoration: none;
  box-shadow:
    0 14px 35px rgba(22, 163, 74, 0.45),
    0 0 0 1px rgba(240, 253, 244, 0.8);
  transition: transform 0.15s ease, box-shadow 0.15s ease, filter 0.15s ease;
}

.primary-btn:hover {
  transform: translateY(-1px);
  filter: brightness(1.02);
  box-shadow:
    0 18px 42px rgba(22, 163, 74, 0.55),
    0 0 0 1px rgba(240, 253, 244, 0.9);
}

.primary-btn:active {
  transform: translateY(0);
  box-shadow:
    0 10px 26px rgba(22, 163, 74, 0.4),
    0 0 0 1px rgba(220, 252, 231, 1);
}



.primary-btn-large {
  min-width: 182px;
  height: 47px;
  font-size: 19px;
}

.hero-helper {
  font-size: 16px;
  color: #6b7280;
}

.hero-helper-center {
  text-align: center;
  margin-top: 6px;
}

.hero-showcase {
  margin-top: 56px;
  text-align: center;
}

.showcase-title {
  font-size: 18px;
  font-weight: 600;
  color: #022c22;
  margin-bottom: 16px;
}

.showcase-track {
  max-width: 1100px;
  margin: 0 auto;
  border-radius: 16px;
  overflow: hidden;
  box-shadow:
    0 18px 45px rgba(15, 23, 42, 0.16),
    0 0 0 1px rgba(148, 163, 184, 0.15);
}

.showcase-track-inner {
  display: flex;
  align-items: center; /* 垂直居中当前图片 */
  transition: transform 0.5s ease;
  transform: translateX(calc(-1 * var(--current-index) * 100%));
}

.showcase-image {
  width: 100%;
  height: auto;
  display: block;
  flex-shrink: 0;
  backface-visibility: hidden;
}

.showcase-dots {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-top: 14px;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  border: none;
  background: #cbd5e1;
  cursor: pointer;
  padding: 0;
  transition: all 0.2s ease;
}

.dot.active {
  width: 22px;
  background: #22c55e;
}

@media (max-width: 768px) {
  .portal-page {
    padding: 24px 16px 40px;
  }

  .portal-header {
    margin-bottom: 24px;
  }

  .hero {
    padding: 24px 18px 26px;
    border-radius: 20px;
  }

  .hero-title {
    font-size: 22px;
  }

  .hero-highlights {
    grid-template-columns: 1fr;
  }
}
</style>

