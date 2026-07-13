import fs from "node:fs/promises";
import path from "node:path";
import { generateImage } from "file:///C:/Users/jaime/Documents/Codex/2026-07-11/curl-https-api-longxiadev-store-v1/outputs/longxia-image-mcp/src/longxia-client.js";

const outDir = "C:/other/新建文件夹/毕设/基于大模型（LLM）的智能穿搭推荐/outputs/longxia-diagrams";
const common = "Clean academic software engineering diagram for a Chinese graduation thesis, landscape 3:2, white background, thin charcoal and muted blue-gray lines, pale gray panels, no gradients, no photos, no decorative effects, no watermark. Leave text labels as blank line areas because labels will be added separately in the thesis document.";
const prompts = {
  flow: `${common} A five-stage left-to-right business flow for an AI outfit recommendation service: user intent input, occasion and weather context, personal wardrobe retrieval, rule validation plus LLM recommendation, outfit result and feedback loop. Use clear arrows and five evenly spaced rounded rectangles with minimalist icons.`,
  usecase: `${common} UML use case diagram with two simple actor silhouettes on the sides and grouped oval use cases in the middle. User actor connects to sign in, wardrobe, outfit recommendation, archives, preferences. Administrator actor connects to tag rules and quality review. Clean relationship lines, professional UML layout.`,
  architecture: `${common} Four-layer system architecture diagram. Top presentation layer, then backend business service layer, then AI orchestration layer, then data infrastructure layer. Each layer contains three modular rounded rectangles, with vertical dependency arrows. Minimalist enterprise architecture visual.`,
  er: `${common} Entity relationship diagram for a fashion recommendation system. Six entities in a balanced layout: user, garment, recommendation session, outfit plan, outfit item, favorite feedback. Use crisp entity boxes, short field-line placeholders, and 1-to-many connectors.`,
  sequence: `${common} UML sequence diagram for generating an outfit recommendation. Five vertical lifelines: user interface, API controller, recommendation service, wardrobe retriever, LLM adapter. Show eight alternating horizontal messages and a return result. Neat dashed lifelines and activation bars.`
};

await fs.mkdir(outDir, { recursive: true });
for (const [name, prompt] of Object.entries(prompts)) {
  const result = await generateImage({ model: "gpt-image-2", prompt, size: "1536x1024" });
  const image = result?.data?.[0];
  if (!image) throw new Error(`No image returned for ${name}`);
  const target = path.join(outDir, `${name}.png`);
  if (image.b64_json) {
    await fs.writeFile(target, Buffer.from(image.b64_json, "base64"));
  } else if (image.url) {
    const response = await fetch(image.url);
    if (!response.ok) throw new Error(`Download failed for ${name}: ${response.status}`);
    await fs.writeFile(target, Buffer.from(await response.arrayBuffer()));
  } else {
    throw new Error(`No image payload returned for ${name}`);
  }
  console.log(target);
}
