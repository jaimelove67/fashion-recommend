<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  grid: { type: Number, default: 15 },
  mouse: { type: Number, default: 0.1 },
  strength: { type: Number, default: 0.15 },
  relaxation: { type: Number, default: 0.9 },
  imageSrc: { type: String, required: true },
  className: { type: String, default: '' }
})

const containerRef = ref(null)

const vertexShader = `
uniform float time;
varying vec2 vUv;

void main() {
  vUv = uv;
  gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
}
`

const fragmentShader = `
uniform sampler2D uDataTexture;
uniform sampler2D uTexture;
varying vec2 vUv;

void main() {
  vec2 uv = vUv;
  vec4 offset = texture2D(uDataTexture, vUv);
  gl_FragColor = texture2D(uTexture, uv - 0.02 * offset.rg);
}
`

let cleanup = null
let initToken = 0

async function init() {
  cleanup?.()

  const container = containerRef.value
  if (!container) return

  const token = ++initToken
  const reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  if (reducedMotion) return

  let THREE
  try {
    THREE = await import('three')
  } catch (error) {
    return
  }

  if (token !== initToken || containerRef.value !== container) return

  let renderer
  let scene
  let camera
  let plane = null
  let geometry = null
  let material = null
  let texture = null
  let dataTexture = null
  let animationId = null
  let resizeObserver = null
  let disposed = false

  try {
    scene = new THREE.Scene()
    renderer = new THREE.WebGLRenderer({
      antialias: true,
      alpha: true,
      powerPreference: 'high-performance'
    })
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.75))
    renderer.setClearColor(0x000000, 0)
    renderer.domElement.className = 'grid-distortion-canvas'
    renderer.domElement.setAttribute('aria-hidden', 'true')

    container.appendChild(renderer.domElement)

    camera = new THREE.OrthographicCamera(0, 0, 0, 0, -1000, 1000)
    camera.position.z = 2

    const uniforms = {
      time: { value: 0 },
      uTexture: { value: null },
      uDataTexture: { value: null }
    }

    const handleResize = () => {
      if (!renderer || !camera) return

      const rect = container.getBoundingClientRect()
      if (!rect.width || !rect.height) return

      const containerAspect = rect.width / rect.height
      renderer.setSize(rect.width, rect.height, false)

      if (plane) plane.scale.set(containerAspect, 1, 1)

      camera.left = -containerAspect / 2
      camera.right = containerAspect / 2
      camera.top = 0.5
      camera.bottom = -0.5
      camera.updateProjectionMatrix()
    }

    const size = Math.max(2, Math.round(props.grid))
    const data = new Float32Array(4 * size * size)
    for (let index = 0; index < size * size; index += 1) {
      data[index * 4] = Math.random() * 255 - 125
      data[index * 4 + 1] = Math.random() * 255 - 125
    }

    dataTexture = new THREE.DataTexture(data, size, size, THREE.RGBAFormat, THREE.FloatType)
    dataTexture.needsUpdate = true
    uniforms.uDataTexture.value = dataTexture

    const textureLoader = new THREE.TextureLoader()
    textureLoader.load(
      props.imageSrc,
      (loadedTexture) => {
        if (disposed) {
          loadedTexture.dispose()
          return
        }

        texture = loadedTexture
        texture.minFilter = THREE.LinearFilter
        texture.magFilter = THREE.LinearFilter
        texture.wrapS = THREE.ClampToEdgeWrapping
        texture.wrapT = THREE.ClampToEdgeWrapping
        uniforms.uTexture.value = texture

        material = new THREE.ShaderMaterial({
          side: THREE.DoubleSide,
          uniforms,
          vertexShader,
          fragmentShader,
          transparent: true
        })
        geometry = new THREE.PlaneGeometry(1, 1, size - 1, size - 1)
        plane = new THREE.Mesh(geometry, material)
        scene.add(plane)
        handleResize()
      },
      undefined,
      () => {
        // The component keeps its regular image fallback when WebGL cannot load it.
      }
    )

    const mouseState = {
      x: 0,
      y: 0,
      prevX: 0,
      prevY: 0,
      vX: 0,
      vY: 0
    }
    const handlePointerMove = (event) => {
      const rect = container.getBoundingClientRect()
      if (!rect.width || !rect.height) return

      const x = (event.clientX - rect.left) / rect.width
      const y = 1 - (event.clientY - rect.top) / rect.height
      mouseState.vX = x - mouseState.prevX
      mouseState.vY = y - mouseState.prevY
      Object.assign(mouseState, { x, y, prevX: x, prevY: y })
    }

    const handlePointerLeave = () => {
      Object.assign(mouseState, { x: 0, y: 0, prevX: 0, prevY: 0, vX: 0, vY: 0 })
    }

    container.addEventListener('pointermove', handlePointerMove)
    container.addEventListener('pointerleave', handlePointerLeave)

    if (window.ResizeObserver) {
      resizeObserver = new ResizeObserver(handleResize)
      resizeObserver.observe(container)
    } else {
      window.addEventListener('resize', handleResize)
    }

    handleResize()

    const animate = () => {
      animationId = window.requestAnimationFrame(animate)
      if (!renderer || !scene || !camera) return

      uniforms.time.value += 0.05
      const distortionData = dataTexture.image.data
      const canDistort = !reducedMotion && distortionData instanceof Float32Array

      for (let index = 0; index < size * size; index += 1) {
        distortionData[index * 4] *= props.relaxation
        distortionData[index * 4 + 1] *= props.relaxation
      }

      if (canDistort) {
        const gridMouseX = size * mouseState.x
        const gridMouseY = size * mouseState.y
        const maxDist = size * props.mouse

        for (let x = 0; x < size; x += 1) {
          for (let y = 0; y < size; y += 1) {
            const distanceSquared = (gridMouseX - x) ** 2 + (gridMouseY - y) ** 2
            if (distanceSquared < maxDist * maxDist) {
              const index = 4 * (x + size * y)
              const power = Math.min(maxDist / Math.sqrt(distanceSquared || 1), 10)
              distortionData[index] += props.strength * 100 * mouseState.vX * power
              distortionData[index + 1] -= props.strength * 100 * mouseState.vY * power
            }
          }
        }
      }

      dataTexture.needsUpdate = true
      renderer.render(scene, camera)
    }

    animate()

    cleanup = () => {
      disposed = true
      if (animationId) window.cancelAnimationFrame(animationId)
      resizeObserver?.disconnect()
      if (!resizeObserver) window.removeEventListener('resize', handleResize)
      container.removeEventListener('pointermove', handlePointerMove)
      container.removeEventListener('pointerleave', handlePointerLeave)
      if (plane) scene.remove(plane)
      geometry?.dispose()
      material?.dispose()
      dataTexture?.dispose()
      texture?.dispose()
      renderer?.dispose()
      renderer?.forceContextLoss?.()
      if (renderer?.domElement && container.contains(renderer.domElement)) {
        container.removeChild(renderer.domElement)
      }
      cleanup = null
    }
  } catch (error) {
    // Keep the image fallback visible on browsers without WebGL support.
    renderer?.dispose?.()
    if (renderer?.domElement && container.contains(renderer.domElement)) {
      container.removeChild(renderer.domElement)
    }
    cleanup = null
  }
}

onMounted(init)

watch(
  () => [props.imageSrc, props.grid, props.mouse, props.strength, props.relaxation],
  init
)

onBeforeUnmount(() => {
  initToken += 1
  cleanup?.()
})
</script>

<template>
  <div ref="containerRef" :class="['grid-distortion', props.className]">
    <img class="grid-distortion-fallback" :src="imageSrc" alt="" aria-hidden="true" />
  </div>
</template>

<style scoped>
.grid-distortion {
  position: relative;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.grid-distortion-fallback,
.grid-distortion-canvas {
  position: absolute;
  inset: 0;
  display: block;
  width: 100%;
  height: 100%;
}

.grid-distortion-fallback {
  object-fit: cover;
}

.grid-distortion-canvas {
  pointer-events: none;
}
</style>
