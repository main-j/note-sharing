<template>
  <div class="auth-container">
    <!-- 高级渐变背景层 -->
    <div class="gradient-background">
      <div class="gradient-orb orb-1"></div>
      <div class="gradient-orb orb-2"></div>
      <div class="gradient-orb orb-3"></div>
    </div>
    
    <!-- 网格背景 -->
    <div class="grid-background"></div>
    
    <!-- 笔记页传送带背景 - 多列倾斜排列 -->
    <div class="notes-conveyor-wrapper">
      <!-- 第1列 - 向上滚动 -->
      <div class="conveyor-column column-1">
        <div class="conveyor-track">
          <!-- 重复两遍以实现无缝循环 -->
          <template v-for="repeat in 2" :key="`repeat-${repeat}`">
            <div 
              v-for="i in 8" 
              :key="`col1-${repeat}-${i}`" 
              class="note-item"
            >
              <svg class="note-icon" viewBox="0 0 480 640" xmlns="http://www.w3.org/2000/svg">
                <defs>
                  <!-- 纸张渐变 -->
                  <linearGradient id="paperGradient" x1="0%" y1="0%" x2="0%" y2="100%">
                    <stop offset="0%" style="stop-color:#ffffff;stop-opacity:0.98" />
                    <stop offset="50%" style="stop-color:#fefefe;stop-opacity:0.96" />
                    <stop offset="100%" style="stop-color:#fafafa;stop-opacity:0.94" />
                  </linearGradient>
                  <!-- 阴影滤镜 -->
                  <filter id="paperShadow" x="-50%" y="-50%" width="200%" height="200%">
                    <feGaussianBlur in="SourceAlpha" stdDeviation="4"/>
                    <feOffset dx="0" dy="4" result="offsetblur"/>
                    <feComponentTransfer>
                      <feFuncA type="linear" slope="0.3"/>
                    </feComponentTransfer>
                    <feMerge>
                      <feMergeNode/>
                      <feMergeNode in="SourceGraphic"/>
                    </feMerge>
                  </filter>
                  <!-- 纹理图案 -->
                  <pattern id="paperTexture" patternUnits="userSpaceOnUse" width="4" height="4">
                    <rect width="4" height="4" fill="#ffffff" opacity="0.02"/>
                    <circle cx="2" cy="2" r="0.5" fill="#000000" opacity="0.03"/>
                  </pattern>
                </defs>
                <!-- 纸张背景 - 带渐变和阴影 -->
                <rect x="0" y="0" width="480" height="640" rx="20" fill="url(#paperGradient)" filter="url(#paperShadow)"/>
                <!-- 纹理层 -->
                <rect x="0" y="0" width="480" height="640" rx="20" fill="url(#paperTexture)"/>
                <!-- 左侧装订线 - 更明显 -->
                <line x1="50" y1="0" x2="50" y2="640" stroke="#cbd5e1" stroke-width="3.5" opacity="0.8"/>
                <!-- 装订孔 -->
                <circle cx="50" cy="100" r="3" fill="#94a3b8" opacity="0.6"/>
                <circle cx="50" cy="320" r="3" fill="#94a3b8" opacity="0.6"/>
                <circle cx="50" cy="540" r="3" fill="#94a3b8" opacity="0.6"/>
                <!-- 横线 - 更清晰 -->
                <line x1="70" y1="70" x2="440" y2="70" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="140" x2="440" y2="140" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="210" x2="440" y2="210" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="280" x2="440" y2="280" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="350" x2="440" y2="350" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="420" x2="440" y2="420" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="490" x2="440" y2="490" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="560" x2="440" y2="560" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <!-- 右侧边缘阴影 -->
                <rect x="470" y="0" width="10" height="640" rx="0" fill="rgba(0,0,0,0.05)"/>
              </svg>
            </div>
          </template>
        </div>
      </div>
      
      <!-- 第2列 - 向下滚动 -->
      <div class="conveyor-column column-2">
        <div class="conveyor-track">
          <template v-for="repeat in 2" :key="`repeat-${repeat}`">
            <div 
              v-for="i in 8" 
              :key="`col2-${repeat}-${i}`" 
              class="note-item"
            >
              <svg class="note-icon" viewBox="0 0 480 640" xmlns="http://www.w3.org/2000/svg">
                <defs>
                  <!-- 纸张渐变 -->
                  <linearGradient id="paperGradient" x1="0%" y1="0%" x2="0%" y2="100%">
                    <stop offset="0%" style="stop-color:#ffffff;stop-opacity:0.98" />
                    <stop offset="50%" style="stop-color:#fefefe;stop-opacity:0.96" />
                    <stop offset="100%" style="stop-color:#fafafa;stop-opacity:0.94" />
                  </linearGradient>
                  <!-- 阴影滤镜 -->
                  <filter id="paperShadow" x="-50%" y="-50%" width="200%" height="200%">
                    <feGaussianBlur in="SourceAlpha" stdDeviation="4"/>
                    <feOffset dx="0" dy="4" result="offsetblur"/>
                    <feComponentTransfer>
                      <feFuncA type="linear" slope="0.3"/>
                    </feComponentTransfer>
                    <feMerge>
                      <feMergeNode/>
                      <feMergeNode in="SourceGraphic"/>
                    </feMerge>
                  </filter>
                  <!-- 纹理图案 -->
                  <pattern id="paperTexture" patternUnits="userSpaceOnUse" width="4" height="4">
                    <rect width="4" height="4" fill="#ffffff" opacity="0.02"/>
                    <circle cx="2" cy="2" r="0.5" fill="#000000" opacity="0.03"/>
                  </pattern>
                </defs>
                <!-- 纸张背景 - 带渐变和阴影 -->
                <rect x="0" y="0" width="480" height="640" rx="20" fill="url(#paperGradient)" filter="url(#paperShadow)"/>
                <!-- 纹理层 -->
                <rect x="0" y="0" width="480" height="640" rx="20" fill="url(#paperTexture)"/>
                <!-- 左侧装订线 - 更明显 -->
                <line x1="50" y1="0" x2="50" y2="640" stroke="#cbd5e1" stroke-width="3.5" opacity="0.8"/>
                <!-- 装订孔 -->
                <circle cx="50" cy="100" r="3" fill="#94a3b8" opacity="0.6"/>
                <circle cx="50" cy="320" r="3" fill="#94a3b8" opacity="0.6"/>
                <circle cx="50" cy="540" r="3" fill="#94a3b8" opacity="0.6"/>
                <!-- 横线 - 更清晰 -->
                <line x1="70" y1="70" x2="440" y2="70" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="140" x2="440" y2="140" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="210" x2="440" y2="210" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="280" x2="440" y2="280" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="350" x2="440" y2="350" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="420" x2="440" y2="420" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="490" x2="440" y2="490" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="560" x2="440" y2="560" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <!-- 右侧边缘阴影 -->
                <rect x="470" y="0" width="10" height="640" rx="0" fill="rgba(0,0,0,0.05)"/>
              </svg>
            </div>
          </template>
        </div>
      </div>
      
      <!-- 第3列 - 向上滚动 -->
      <div class="conveyor-column column-3">
        <div class="conveyor-track">
          <template v-for="repeat in 2" :key="`repeat-${repeat}`">
            <div 
              v-for="i in 8" 
              :key="`col3-${repeat}-${i}`" 
              class="note-item"
            >
              <svg class="note-icon" viewBox="0 0 480 640" xmlns="http://www.w3.org/2000/svg">
                <defs>
                  <!-- 纸张渐变 -->
                  <linearGradient id="paperGradient" x1="0%" y1="0%" x2="0%" y2="100%">
                    <stop offset="0%" style="stop-color:#ffffff;stop-opacity:0.98" />
                    <stop offset="50%" style="stop-color:#fefefe;stop-opacity:0.96" />
                    <stop offset="100%" style="stop-color:#fafafa;stop-opacity:0.94" />
                  </linearGradient>
                  <!-- 阴影滤镜 -->
                  <filter id="paperShadow" x="-50%" y="-50%" width="200%" height="200%">
                    <feGaussianBlur in="SourceAlpha" stdDeviation="4"/>
                    <feOffset dx="0" dy="4" result="offsetblur"/>
                    <feComponentTransfer>
                      <feFuncA type="linear" slope="0.3"/>
                    </feComponentTransfer>
                    <feMerge>
                      <feMergeNode/>
                      <feMergeNode in="SourceGraphic"/>
                    </feMerge>
                  </filter>
                  <!-- 纹理图案 -->
                  <pattern id="paperTexture" patternUnits="userSpaceOnUse" width="4" height="4">
                    <rect width="4" height="4" fill="#ffffff" opacity="0.02"/>
                    <circle cx="2" cy="2" r="0.5" fill="#000000" opacity="0.03"/>
                  </pattern>
                </defs>
                <!-- 纸张背景 - 带渐变和阴影 -->
                <rect x="0" y="0" width="480" height="640" rx="20" fill="url(#paperGradient)" filter="url(#paperShadow)"/>
                <!-- 纹理层 -->
                <rect x="0" y="0" width="480" height="640" rx="20" fill="url(#paperTexture)"/>
                <!-- 左侧装订线 - 更明显 -->
                <line x1="50" y1="0" x2="50" y2="640" stroke="#cbd5e1" stroke-width="3.5" opacity="0.8"/>
                <!-- 装订孔 -->
                <circle cx="50" cy="100" r="3" fill="#94a3b8" opacity="0.6"/>
                <circle cx="50" cy="320" r="3" fill="#94a3b8" opacity="0.6"/>
                <circle cx="50" cy="540" r="3" fill="#94a3b8" opacity="0.6"/>
                <!-- 横线 - 更清晰 -->
                <line x1="70" y1="70" x2="440" y2="70" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="140" x2="440" y2="140" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="210" x2="440" y2="210" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="280" x2="440" y2="280" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="350" x2="440" y2="350" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="420" x2="440" y2="420" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="490" x2="440" y2="490" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="560" x2="440" y2="560" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <!-- 右侧边缘阴影 -->
                <rect x="470" y="0" width="10" height="640" rx="0" fill="rgba(0,0,0,0.05)"/>
              </svg>
            </div>
          </template>
        </div>
      </div>
      
      <!-- 第4列 - 向下滚动 -->
      <div class="conveyor-column column-4">
        <div class="conveyor-track">
          <template v-for="repeat in 2" :key="`repeat-${repeat}`">
            <div 
              v-for="i in 8" 
              :key="`col4-${repeat}-${i}`" 
              class="note-item"
            >
              <svg class="note-icon" viewBox="0 0 480 640" xmlns="http://www.w3.org/2000/svg">
                <defs>
                  <!-- 纸张渐变 -->
                  <linearGradient id="paperGradient" x1="0%" y1="0%" x2="0%" y2="100%">
                    <stop offset="0%" style="stop-color:#ffffff;stop-opacity:0.98" />
                    <stop offset="50%" style="stop-color:#fefefe;stop-opacity:0.96" />
                    <stop offset="100%" style="stop-color:#fafafa;stop-opacity:0.94" />
                  </linearGradient>
                  <!-- 阴影滤镜 -->
                  <filter id="paperShadow" x="-50%" y="-50%" width="200%" height="200%">
                    <feGaussianBlur in="SourceAlpha" stdDeviation="4"/>
                    <feOffset dx="0" dy="4" result="offsetblur"/>
                    <feComponentTransfer>
                      <feFuncA type="linear" slope="0.3"/>
                    </feComponentTransfer>
                    <feMerge>
                      <feMergeNode/>
                      <feMergeNode in="SourceGraphic"/>
                    </feMerge>
                  </filter>
                  <!-- 纹理图案 -->
                  <pattern id="paperTexture" patternUnits="userSpaceOnUse" width="4" height="4">
                    <rect width="4" height="4" fill="#ffffff" opacity="0.02"/>
                    <circle cx="2" cy="2" r="0.5" fill="#000000" opacity="0.03"/>
                  </pattern>
                </defs>
                <!-- 纸张背景 - 带渐变和阴影 -->
                <rect x="0" y="0" width="480" height="640" rx="20" fill="url(#paperGradient)" filter="url(#paperShadow)"/>
                <!-- 纹理层 -->
                <rect x="0" y="0" width="480" height="640" rx="20" fill="url(#paperTexture)"/>
                <!-- 左侧装订线 - 更明显 -->
                <line x1="50" y1="0" x2="50" y2="640" stroke="#cbd5e1" stroke-width="3.5" opacity="0.8"/>
                <!-- 装订孔 -->
                <circle cx="50" cy="100" r="3" fill="#94a3b8" opacity="0.6"/>
                <circle cx="50" cy="320" r="3" fill="#94a3b8" opacity="0.6"/>
                <circle cx="50" cy="540" r="3" fill="#94a3b8" opacity="0.6"/>
                <!-- 横线 - 更清晰 -->
                <line x1="70" y1="70" x2="440" y2="70" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="140" x2="440" y2="140" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="210" x2="440" y2="210" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="280" x2="440" y2="280" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="350" x2="440" y2="350" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="420" x2="440" y2="420" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="490" x2="440" y2="490" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="560" x2="440" y2="560" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <!-- 右侧边缘阴影 -->
                <rect x="470" y="0" width="10" height="640" rx="0" fill="rgba(0,0,0,0.05)"/>
              </svg>
            </div>
          </template>
        </div>
      </div>
      
      <!-- 第5列 - 向上滚动 -->
      <div class="conveyor-column column-5">
        <div class="conveyor-track">
          <template v-for="repeat in 2" :key="`repeat-${repeat}`">
            <div 
              v-for="i in 8" 
              :key="`col5-${repeat}-${i}`" 
              class="note-item"
            >
              <svg class="note-icon" viewBox="0 0 480 640" xmlns="http://www.w3.org/2000/svg">
                <defs>
                  <!-- 纸张渐变 -->
                  <linearGradient id="paperGradient" x1="0%" y1="0%" x2="0%" y2="100%">
                    <stop offset="0%" style="stop-color:#ffffff;stop-opacity:0.98" />
                    <stop offset="50%" style="stop-color:#fefefe;stop-opacity:0.96" />
                    <stop offset="100%" style="stop-color:#fafafa;stop-opacity:0.94" />
                  </linearGradient>
                  <!-- 阴影滤镜 -->
                  <filter id="paperShadow" x="-50%" y="-50%" width="200%" height="200%">
                    <feGaussianBlur in="SourceAlpha" stdDeviation="4"/>
                    <feOffset dx="0" dy="4" result="offsetblur"/>
                    <feComponentTransfer>
                      <feFuncA type="linear" slope="0.3"/>
                    </feComponentTransfer>
                    <feMerge>
                      <feMergeNode/>
                      <feMergeNode in="SourceGraphic"/>
                    </feMerge>
                  </filter>
                  <!-- 纹理图案 -->
                  <pattern id="paperTexture" patternUnits="userSpaceOnUse" width="4" height="4">
                    <rect width="4" height="4" fill="#ffffff" opacity="0.02"/>
                    <circle cx="2" cy="2" r="0.5" fill="#000000" opacity="0.03"/>
                  </pattern>
                </defs>
                <!-- 纸张背景 - 带渐变和阴影 -->
                <rect x="0" y="0" width="480" height="640" rx="20" fill="url(#paperGradient)" filter="url(#paperShadow)"/>
                <!-- 纹理层 -->
                <rect x="0" y="0" width="480" height="640" rx="20" fill="url(#paperTexture)"/>
                <!-- 左侧装订线 - 更明显 -->
                <line x1="50" y1="0" x2="50" y2="640" stroke="#cbd5e1" stroke-width="3.5" opacity="0.8"/>
                <!-- 装订孔 -->
                <circle cx="50" cy="100" r="3" fill="#94a3b8" opacity="0.6"/>
                <circle cx="50" cy="320" r="3" fill="#94a3b8" opacity="0.6"/>
                <circle cx="50" cy="540" r="3" fill="#94a3b8" opacity="0.6"/>
                <!-- 横线 - 更清晰 -->
                <line x1="70" y1="70" x2="440" y2="70" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="140" x2="440" y2="140" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="210" x2="440" y2="210" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="280" x2="440" y2="280" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="350" x2="440" y2="350" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="420" x2="440" y2="420" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="490" x2="440" y2="490" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="560" x2="440" y2="560" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <!-- 右侧边缘阴影 -->
                <rect x="470" y="0" width="10" height="640" rx="0" fill="rgba(0,0,0,0.05)"/>
              </svg>
            </div>
          </template>
        </div>
      </div>
      
      <!-- 第6列 - 向下滚动 -->
      <div class="conveyor-column column-6">
        <div class="conveyor-track">
          <template v-for="repeat in 2" :key="`repeat-${repeat}`">
            <div 
              v-for="i in 8" 
              :key="`col6-${repeat}-${i}`" 
              class="note-item"
            >
              <svg class="note-icon" viewBox="0 0 480 640" xmlns="http://www.w3.org/2000/svg">
                <defs>
                  <!-- 纸张渐变 -->
                  <linearGradient id="paperGradient6" x1="0%" y1="0%" x2="0%" y2="100%">
                    <stop offset="0%" style="stop-color:#ffffff;stop-opacity:0.98" />
                    <stop offset="50%" style="stop-color:#fefefe;stop-opacity:0.96" />
                    <stop offset="100%" style="stop-color:#fafafa;stop-opacity:0.94" />
                  </linearGradient>
                  <!-- 阴影滤镜 -->
                  <filter id="paperShadow6" x="-50%" y="-50%" width="200%" height="200%">
                    <feGaussianBlur in="SourceAlpha" stdDeviation="4"/>
                    <feOffset dx="0" dy="4" result="offsetblur"/>
                    <feComponentTransfer>
                      <feFuncA type="linear" slope="0.3"/>
                    </feComponentTransfer>
                    <feMerge>
                      <feMergeNode/>
                      <feMergeNode in="SourceGraphic"/>
                    </feMerge>
                  </filter>
                  <!-- 纹理图案 -->
                  <pattern id="paperTexture6" patternUnits="userSpaceOnUse" width="4" height="4">
                    <rect width="4" height="4" fill="#ffffff" opacity="0.02"/>
                    <circle cx="2" cy="2" r="0.5" fill="#000000" opacity="0.03"/>
                  </pattern>
                </defs>
                <!-- 纸张背景 - 带渐变和阴影 -->
                <rect x="0" y="0" width="480" height="640" rx="20" fill="url(#paperGradient6)" filter="url(#paperShadow6)"/>
                <!-- 纹理层 -->
                <rect x="0" y="0" width="480" height="640" rx="20" fill="url(#paperTexture6)"/>
                <!-- 左侧装订线 - 更明显 -->
                <line x1="50" y1="0" x2="50" y2="640" stroke="#cbd5e1" stroke-width="3.5" opacity="0.8"/>
                <!-- 装订孔 -->
                <circle cx="50" cy="100" r="3" fill="#94a3b8" opacity="0.6"/>
                <circle cx="50" cy="320" r="3" fill="#94a3b8" opacity="0.6"/>
                <circle cx="50" cy="540" r="3" fill="#94a3b8" opacity="0.6"/>
                <!-- 横线 - 更清晰 -->
                <line x1="70" y1="70" x2="440" y2="70" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="140" x2="440" y2="140" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="210" x2="440" y2="210" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="280" x2="440" y2="280" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="350" x2="440" y2="350" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="420" x2="440" y2="420" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="490" x2="440" y2="490" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="560" x2="440" y2="560" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <!-- 右侧边缘阴影 -->
                <rect x="470" y="0" width="10" height="640" rx="0" fill="rgba(0,0,0,0.05)"/>
              </svg>
            </div>
          </template>
        </div>
      </div>
      
      <!-- 第7列 - 向上滚动 -->
      <div class="conveyor-column column-7">
        <div class="conveyor-track">
          <template v-for="repeat in 2" :key="`repeat-${repeat}`">
            <div 
              v-for="i in 8" 
              :key="`col7-${repeat}-${i}`" 
              class="note-item"
            >
              <svg class="note-icon" viewBox="0 0 480 640" xmlns="http://www.w3.org/2000/svg">
                <defs>
                  <!-- 纸张渐变 -->
                  <linearGradient id="paperGradient7" x1="0%" y1="0%" x2="0%" y2="100%">
                    <stop offset="0%" style="stop-color:#ffffff;stop-opacity:0.98" />
                    <stop offset="50%" style="stop-color:#fefefe;stop-opacity:0.96" />
                    <stop offset="100%" style="stop-color:#fafafa;stop-opacity:0.94" />
                  </linearGradient>
                  <!-- 阴影滤镜 -->
                  <filter id="paperShadow7" x="-50%" y="-50%" width="200%" height="200%">
                    <feGaussianBlur in="SourceAlpha" stdDeviation="4"/>
                    <feOffset dx="0" dy="4" result="offsetblur"/>
                    <feComponentTransfer>
                      <feFuncA type="linear" slope="0.3"/>
                    </feComponentTransfer>
                    <feMerge>
                      <feMergeNode/>
                      <feMergeNode in="SourceGraphic"/>
                    </feMerge>
                  </filter>
                  <!-- 纹理图案 -->
                  <pattern id="paperTexture7" patternUnits="userSpaceOnUse" width="4" height="4">
                    <rect width="4" height="4" fill="#ffffff" opacity="0.02"/>
                    <circle cx="2" cy="2" r="0.5" fill="#000000" opacity="0.03"/>
                  </pattern>
                </defs>
                <!-- 纸张背景 - 带渐变和阴影 -->
                <rect x="0" y="0" width="480" height="640" rx="20" fill="url(#paperGradient7)" filter="url(#paperShadow7)"/>
                <!-- 纹理层 -->
                <rect x="0" y="0" width="480" height="640" rx="20" fill="url(#paperTexture7)"/>
                <!-- 左侧装订线 - 更明显 -->
                <line x1="50" y1="0" x2="50" y2="640" stroke="#cbd5e1" stroke-width="3.5" opacity="0.8"/>
                <!-- 装订孔 -->
                <circle cx="50" cy="100" r="3" fill="#94a3b8" opacity="0.6"/>
                <circle cx="50" cy="320" r="3" fill="#94a3b8" opacity="0.6"/>
                <circle cx="50" cy="540" r="3" fill="#94a3b8" opacity="0.6"/>
                <!-- 横线 - 更清晰 -->
                <line x1="70" y1="70" x2="440" y2="70" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="140" x2="440" y2="140" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="210" x2="440" y2="210" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="280" x2="440" y2="280" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="350" x2="440" y2="350" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="420" x2="440" y2="420" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="490" x2="440" y2="490" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="560" x2="440" y2="560" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <!-- 右侧边缘阴影 -->
                <rect x="470" y="0" width="10" height="640" rx="0" fill="rgba(0,0,0,0.05)"/>
              </svg>
            </div>
          </template>
        </div>
      </div>
      
      <!-- 第8列 - 向下滚动 -->
      <div class="conveyor-column column-8">
        <div class="conveyor-track">
          <template v-for="repeat in 2" :key="`repeat-${repeat}`">
            <div 
              v-for="i in 8" 
              :key="`col8-${repeat}-${i}`" 
              class="note-item"
            >
              <svg class="note-icon" viewBox="0 0 480 640" xmlns="http://www.w3.org/2000/svg">
                <defs>
                  <!-- 纸张渐变 -->
                  <linearGradient id="paperGradient8" x1="0%" y1="0%" x2="0%" y2="100%">
                    <stop offset="0%" style="stop-color:#ffffff;stop-opacity:0.98" />
                    <stop offset="50%" style="stop-color:#fefefe;stop-opacity:0.96" />
                    <stop offset="100%" style="stop-color:#fafafa;stop-opacity:0.94" />
                  </linearGradient>
                  <!-- 阴影滤镜 -->
                  <filter id="paperShadow8" x="-50%" y="-50%" width="200%" height="200%">
                    <feGaussianBlur in="SourceAlpha" stdDeviation="4"/>
                    <feOffset dx="0" dy="4" result="offsetblur"/>
                    <feComponentTransfer>
                      <feFuncA type="linear" slope="0.3"/>
                    </feComponentTransfer>
                    <feMerge>
                      <feMergeNode/>
                      <feMergeNode in="SourceGraphic"/>
                    </feMerge>
                  </filter>
                  <!-- 纹理图案 -->
                  <pattern id="paperTexture8" patternUnits="userSpaceOnUse" width="4" height="4">
                    <rect width="4" height="4" fill="#ffffff" opacity="0.02"/>
                    <circle cx="2" cy="2" r="0.5" fill="#000000" opacity="0.03"/>
                  </pattern>
                </defs>
                <!-- 纸张背景 - 带渐变和阴影 -->
                <rect x="0" y="0" width="480" height="640" rx="20" fill="url(#paperGradient8)" filter="url(#paperShadow8)"/>
                <!-- 纹理层 -->
                <rect x="0" y="0" width="480" height="640" rx="20" fill="url(#paperTexture8)"/>
                <!-- 左侧装订线 - 更明显 -->
                <line x1="50" y1="0" x2="50" y2="640" stroke="#cbd5e1" stroke-width="3.5" opacity="0.8"/>
                <!-- 装订孔 -->
                <circle cx="50" cy="100" r="3" fill="#94a3b8" opacity="0.6"/>
                <circle cx="50" cy="320" r="3" fill="#94a3b8" opacity="0.6"/>
                <circle cx="50" cy="540" r="3" fill="#94a3b8" opacity="0.6"/>
                <!-- 横线 - 更清晰 -->
                <line x1="70" y1="70" x2="440" y2="70" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="140" x2="440" y2="140" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="210" x2="440" y2="210" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="280" x2="440" y2="280" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="350" x2="440" y2="350" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="420" x2="440" y2="420" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="490" x2="440" y2="490" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="560" x2="440" y2="560" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <!-- 右侧边缘阴影 -->
                <rect x="470" y="0" width="10" height="640" rx="0" fill="rgba(0,0,0,0.05)"/>
              </svg>
            </div>
          </template>
        </div>
      </div>
      
      <!-- 第9列 - 向上滚动 -->
      <div class="conveyor-column column-9">
        <div class="conveyor-track">
          <template v-for="repeat in 2" :key="`repeat-${repeat}`">
            <div 
              v-for="i in 8" 
              :key="`col9-${repeat}-${i}`" 
              class="note-item"
            >
              <svg class="note-icon" viewBox="0 0 480 640" xmlns="http://www.w3.org/2000/svg">
                <defs>
                  <!-- 纸张渐变 -->
                  <linearGradient id="paperGradient9" x1="0%" y1="0%" x2="0%" y2="100%">
                    <stop offset="0%" style="stop-color:#ffffff;stop-opacity:0.98" />
                    <stop offset="50%" style="stop-color:#fefefe;stop-opacity:0.96" />
                    <stop offset="100%" style="stop-color:#fafafa;stop-opacity:0.94" />
                  </linearGradient>
                  <!-- 阴影滤镜 -->
                  <filter id="paperShadow9" x="-50%" y="-50%" width="200%" height="200%">
                    <feGaussianBlur in="SourceAlpha" stdDeviation="4"/>
                    <feOffset dx="0" dy="4" result="offsetblur"/>
                    <feComponentTransfer>
                      <feFuncA type="linear" slope="0.3"/>
                    </feComponentTransfer>
                    <feMerge>
                      <feMergeNode/>
                      <feMergeNode in="SourceGraphic"/>
                    </feMerge>
                  </filter>
                  <!-- 纹理图案 -->
                  <pattern id="paperTexture9" patternUnits="userSpaceOnUse" width="4" height="4">
                    <rect width="4" height="4" fill="#ffffff" opacity="0.02"/>
                    <circle cx="2" cy="2" r="0.5" fill="#000000" opacity="0.03"/>
                  </pattern>
                </defs>
                <!-- 纸张背景 - 带渐变和阴影 -->
                <rect x="0" y="0" width="480" height="640" rx="20" fill="url(#paperGradient9)" filter="url(#paperShadow9)"/>
                <!-- 纹理层 -->
                <rect x="0" y="0" width="480" height="640" rx="20" fill="url(#paperTexture9)"/>
                <!-- 左侧装订线 - 更明显 -->
                <line x1="50" y1="0" x2="50" y2="640" stroke="#cbd5e1" stroke-width="3.5" opacity="0.8"/>
                <!-- 装订孔 -->
                <circle cx="50" cy="100" r="3" fill="#94a3b8" opacity="0.6"/>
                <circle cx="50" cy="320" r="3" fill="#94a3b8" opacity="0.6"/>
                <circle cx="50" cy="540" r="3" fill="#94a3b8" opacity="0.6"/>
                <!-- 横线 - 更清晰 -->
                <line x1="70" y1="70" x2="440" y2="70" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="140" x2="440" y2="140" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="210" x2="440" y2="210" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="280" x2="440" y2="280" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="350" x2="440" y2="350" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="420" x2="440" y2="420" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="490" x2="440" y2="490" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="560" x2="440" y2="560" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <!-- 右侧边缘阴影 -->
                <rect x="470" y="0" width="10" height="640" rx="0" fill="rgba(0,0,0,0.05)"/>
              </svg>
            </div>
          </template>
        </div>
      </div>
      
      <!-- 第10列 - 向下滚动（右下角，部分超出屏幕） -->
      <div class="conveyor-column column-10">
        <div class="conveyor-track">
          <template v-for="repeat in 2" :key="`repeat-${repeat}`">
            <div 
              v-for="i in 8" 
              :key="`col10-${repeat}-${i}`" 
              class="note-item"
            >
              <svg class="note-icon" viewBox="0 0 480 640" xmlns="http://www.w3.org/2000/svg">
                <defs>
                  <!-- 纸张渐变 -->
                  <linearGradient id="paperGradient10" x1="0%" y1="0%" x2="0%" y2="100%">
                    <stop offset="0%" style="stop-color:#ffffff;stop-opacity:0.98" />
                    <stop offset="50%" style="stop-color:#fefefe;stop-opacity:0.96" />
                    <stop offset="100%" style="stop-color:#fafafa;stop-opacity:0.94" />
                  </linearGradient>
                  <!-- 阴影滤镜 -->
                  <filter id="paperShadow10" x="-50%" y="-50%" width="200%" height="200%">
                    <feGaussianBlur in="SourceAlpha" stdDeviation="4"/>
                    <feOffset dx="0" dy="4" result="offsetblur"/>
                    <feComponentTransfer>
                      <feFuncA type="linear" slope="0.3"/>
                    </feComponentTransfer>
                    <feMerge>
                      <feMergeNode/>
                      <feMergeNode in="SourceGraphic"/>
                    </feMerge>
                  </filter>
                  <!-- 纹理图案 -->
                  <pattern id="paperTexture10" patternUnits="userSpaceOnUse" width="4" height="4">
                    <rect width="4" height="4" fill="#ffffff" opacity="0.02"/>
                    <circle cx="2" cy="2" r="0.5" fill="#000000" opacity="0.03"/>
                  </pattern>
                </defs>
                <!-- 纸张背景 - 带渐变和阴影 -->
                <rect x="0" y="0" width="480" height="640" rx="20" fill="url(#paperGradient10)" filter="url(#paperShadow10)"/>
                <!-- 纹理层 -->
                <rect x="0" y="0" width="480" height="640" rx="20" fill="url(#paperTexture10)"/>
                <!-- 左侧装订线 - 更明显 -->
                <line x1="50" y1="0" x2="50" y2="640" stroke="#cbd5e1" stroke-width="3.5" opacity="0.8"/>
                <!-- 装订孔 -->
                <circle cx="50" cy="100" r="3" fill="#94a3b8" opacity="0.6"/>
                <circle cx="50" cy="320" r="3" fill="#94a3b8" opacity="0.6"/>
                <circle cx="50" cy="540" r="3" fill="#94a3b8" opacity="0.6"/>
                <!-- 横线 - 更清晰 -->
                <line x1="70" y1="70" x2="440" y2="70" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="140" x2="440" y2="140" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="210" x2="440" y2="210" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="280" x2="440" y2="280" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="350" x2="440" y2="350" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="420" x2="440" y2="420" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="490" x2="440" y2="490" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <line x1="70" y1="560" x2="440" y2="560" stroke="#94a3b8" stroke-width="2.5" opacity="0.75"/>
                <!-- 右侧边缘阴影 -->
                <rect x="470" y="0" width="10" height="640" rx="0" fill="rgba(0,0,0,0.05)"/>
              </svg>
            </div>
          </template>
        </div>
      </div>
      
    </div>

    <div class="auth-box">
      <!-- 根据路由显示具体组件 -->
      <LoginPage v-if="currentRoute === 'Login'" />
      <RegisterPage v-if="currentRoute === 'Register'" />
      <ForgotPassword v-if="currentRoute === 'ForgotPassword'" />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import LoginPage from '../components/LoginPage.vue'
import RegisterPage from '../components/RegisterPage.vue'
import ForgotPassword from '../components/ForgotPassword.vue'

// 当前路由
const route = useRoute()
const currentRoute = computed(() => route.name)
</script>

<style scoped>
.auth-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  width: 100%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 25%, #f093fb 50%, #4facfe 75%, #00f2fe 100%);
  background-size: 400% 400%;
  animation: gradientShift 15s ease infinite;
  padding: 20px;
  position: relative;
  overflow-x: visible;
  overflow-y: hidden;
}

/* 渐变背景动画 */
@keyframes gradientShift {
  0% {
    background-position: 0% 50%;
  }
  50% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0% 50%;
  }
}

/* 高级渐变背景层 - 光晕效果 */
.gradient-background {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 1;
}

.gradient-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.6;
  animation: orbFloat 20s ease-in-out infinite;
}

.orb-1 {
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(102, 126, 234, 0.8) 0%, rgba(102, 126, 234, 0) 70%);
  top: -200px;
  left: -200px;
  animation-delay: 0s;
}

.orb-2 {
  width: 600px;
  height: 600px;
  background: radial-gradient(circle, rgba(118, 75, 162, 0.7) 0%, rgba(118, 75, 162, 0) 70%);
  bottom: -250px;
  right: -250px;
  animation-delay: -7s;
}

.orb-3 {
  width: 450px;
  height: 450px;
  background: radial-gradient(circle, rgba(79, 172, 254, 0.6) 0%, rgba(79, 172, 254, 0) 70%);
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  animation-delay: -14s;
}

@keyframes orbFloat {
  0%, 100% {
    transform: translate(0, 0) scale(1);
  }
  33% {
    transform: translate(30px, -30px) scale(1.1);
  }
  66% {
    transform: translate(-20px, 20px) scale(0.9);
  }
}

/* 网格背景 */
.grid-background {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-image: 
    linear-gradient(rgba(255, 255, 255, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.03) 1px, transparent 1px);
  background-size: 50px 50px;
  pointer-events: none;
  z-index: 2;
  opacity: 0.5;
}

/* 传送带包装器 - 整体向右倾斜 */
.notes-conveyor-wrapper {
  position: absolute;
  top: -10%;
  left: -15%;
  width: 140%;
  height: 130%;
  overflow: visible;
  pointer-events: none;
  transform: rotate(5deg);
  transform-origin: center center;
  opacity: 0.25;
  padding: 0;
  box-sizing: border-box;
  z-index: 2;
  filter: blur(0.3px);
}

/* 传送带列基础样式 */
.conveyor-column {
  position: absolute;
  width: 300px;
  height: 100%;
  left: 0;
  overflow: hidden;
}

/* 传送带轨道 - 垂直排列，首尾相连 */
.conveyor-track {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
  will-change: transform;
}

/* 笔记项样式 - 高级质感设计 */
.note-item {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 300px;
  height: 520px;
  padding: 0;
  margin: 0 auto;
  flex-shrink: 0;
  filter: drop-shadow(0 6px 20px rgba(0, 0, 0, 0.25)) drop-shadow(0 3px 8px rgba(0, 0, 0, 0.15));
  transition: filter 0.3s ease, transform 0.3s ease;
}

.note-item:hover {
  filter: drop-shadow(0 8px 24px rgba(0, 0, 0, 0.3)) drop-shadow(0 4px 10px rgba(0, 0, 0, 0.2));
  transform: scale(1.02);
}

.note-icon {
  width: 100%;
  height: 100%;
  object-fit: contain;
  opacity: 1;
  filter: brightness(1.08) contrast(1.15) saturate(1.05);
}

/* 第1列 - 向上滚动 */
.column-1 {
  left: -10%;
  animation: columnMoveUp1 40s infinite linear;
}

.column-1 .conveyor-track {
  animation: trackMoveUp 40s infinite linear;
}

/* 第2列 - 向下滚动 */
.column-2 {
  left: 2%;
  animation: columnMoveDown1 45s infinite linear;
}

.column-2 .conveyor-track {
  animation: trackMoveDown 45s infinite linear;
}

/* 第3列 - 向上滚动 */
.column-3 {
  left: 14%;
  animation: columnMoveUp2 50s infinite linear;
}

.column-3 .conveyor-track {
  animation: trackMoveUp 50s infinite linear;
}

/* 第4列 - 向下滚动 */
.column-4 {
  left: 26%;
  animation: columnMoveDown2 42s infinite linear;
}

.column-4 .conveyor-track {
  animation: trackMoveDown 42s infinite linear;
}

/* 第5列 - 向上滚动 */
.column-5 {
  left: 38%;
  animation: columnMoveUp3 48s infinite linear;
}

.column-5 .conveyor-track {
  animation: trackMoveUp 48s infinite linear;
}

/* 第6列 - 向下滚动 */
.column-6 {
  left: 50%;
  animation: columnMoveDown3 44s infinite linear;
}

.column-6 .conveyor-track {
  animation: trackMoveDown 44s infinite linear;
}

/* 第7列 - 向上滚动 */
.column-7 {
  left: 62%;
  animation: columnMoveUp4 46s infinite linear;
}

.column-7 .conveyor-track {
  animation: trackMoveUp 46s infinite linear;
}

/* 第8列 - 向下滚动 */
.column-8 {
  left: 74%;
  animation: columnMoveDown4 43s infinite linear;
}

.column-8 .conveyor-track {
  animation: trackMoveDown 43s infinite linear;
}

/* 第9列 - 向上滚动 */
.column-9 {
  left: 86%;
  animation: columnMoveUp5 47s infinite linear;
}

.column-9 .conveyor-track {
  animation: trackMoveUp 47s infinite linear;
}

/* 第10列 - 向下滚动（右下角，部分超出） */
.column-10 {
  left: 98%;
  animation: columnMoveDown5 45s infinite linear;
}

.column-10 .conveyor-track {
  animation: trackMoveDown 45s infinite linear;
}

/* 向上滚动动画 - 无缝循环（移动一半距离） */
@keyframes trackMoveUp {
  0% {
    transform: translateY(0);
  }
  100% {
    transform: translateY(calc(-50% - 1px));
  }
}

/* 向下滚动动画 - 无缝循环（移动一半距离） */
@keyframes trackMoveDown {
  0% {
    transform: translateY(calc(-50% - 1px));
  }
  100% {
    transform: translateY(0);
  }
}

/* 列的轻微位置变化 - 营造交错节奏（非常缓慢） */
@keyframes columnMoveUp1 {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-3px);
  }
}

@keyframes columnMoveDown1 {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(3px);
  }
}

@keyframes columnMoveUp2 {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-2px);
  }
}

@keyframes columnMoveDown2 {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(2px);
  }
}

@keyframes columnMoveUp3 {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-4px);
  }
}

@keyframes columnMoveDown3 {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(4px);
  }
}

@keyframes columnMoveUp4 {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-3px);
  }
}

@keyframes columnMoveDown4 {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(3px);
  }
}

@keyframes columnMoveUp5 {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-2px);
  }
}

@keyframes columnMoveDown5 {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(4px);
  }
}


.auth-box {
  width: 100%;
  max-width: 440px;
  padding: 48px 40px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(30px) saturate(180%);
  -webkit-backdrop-filter: blur(30px) saturate(180%);
  border-radius: var(--radius-lg);
  box-shadow: 
    0 8px 32px rgba(0, 0, 0, 0.12),
    0 2px 8px rgba(0, 0, 0, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.9);
  position: relative;
  z-index: 10;
  border: 1px solid rgba(255, 255, 255, 0.6);
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}

.auth-box::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  border-radius: var(--radius-lg);
  padding: 1px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.4), rgba(255, 255, 255, 0.1));
  -webkit-mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
  -webkit-mask-composite: xor;
  mask-composite: exclude;
  pointer-events: none;
  z-index: -1;
}

.auth-box:hover {
  transform: translateY(-2px);
  box-shadow: 
    0 12px 40px rgba(0, 0, 0, 0.15),
    0 4px 12px rgba(0, 0, 0, 0.1),
    inset 0 1px 0 rgba(255, 255, 255, 0.9);
}

@media (max-width: 768px) {
  .auth-container {
    background-size: 300% 300%;
  }
  
  .orb-1 {
    width: 400px;
    height: 400px;
  }
  
  .orb-2 {
    width: 500px;
    height: 500px;
  }
  
  .orb-3 {
    width: 350px;
    height: 350px;
  }
  
  .notes-conveyor-wrapper {
    top: -8%;
    left: -12%;
    width: 135%;
    height: 125%;
    overflow: visible;
    transform: rotate(4deg);
    opacity: 0.12;
    padding: 0;
  }

  .conveyor-column {
    width: 240px;
  }

  .note-item {
    width: 240px;
    height: 400px;
  }

  /* 移动端调整列位置 */
  .column-1 { left: -8%; }
  .column-2 { left: 0%; }
  .column-3 { left: 12%; }
  .column-4 { left: 24%; }
  .column-5 { left: 36%; }
  .column-6 { left: 48%; }
  .column-7 { left: 60%; }
  .column-8 { left: 72%; }
  .column-9 { left: 96%; }
  .column-10 { left: 108%; }
}

@media (max-width: 480px) {
  .auth-container {
    background-size: 250% 250%;
  }
  
  .orb-1 {
    width: 300px;
    height: 300px;
  }
  
  .orb-2 {
    width: 400px;
    height: 400px;
  }
  
  .orb-3 {
    width: 250px;
    height: 250px;
  }
  
  .auth-box {
    padding: 36px 24px;
    border-radius: var(--radius-md);
  }

  .notes-conveyor-wrapper {
    top: -6%;
    left: -10%;
    width: 130%;
    height: 120%;
    overflow: visible;
    transform: rotate(3deg);
    opacity: 0.1;
    padding: 0;
  }

  .conveyor-column {
    width: 200px;
  }

  .note-item {
    width: 200px;
    height: 360px;
  }

  /* 移动端调整列位置 */
  .column-1 { left: -6%; }
  .column-2 { left: -1%; }
  .column-3 { left: 10%; }
  .column-4 { left: 22%; }
  .column-5 { left: 34%; }
  .column-6 { left: 46%; }
  .column-7 { left: 58%; }
  .column-8 { left: 70%; }
  .column-9 { left: 94%; }
  .column-10 { left: 106%; }
}
</style>
