# openapi-douyin
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Feahau%2Fdouyin-openapi.svg?type=shield)](https://app.fossa.com/projects/git%2Bgithub.com%2Feahau%2Fdouyin-openapi?ref=badge_shield)


本项目根据 [抖音开放平台](https://developer.open-douyin.com/) 的 OpenAPI 生成符合 [openapi](https://www.openapis.org/) 规范的 json 文件
目前支持以下 api

| 抖音 api | openapi.json |
| --- | --- |
| [小程序](https://developer.open-douyin.com/docs/resource/zh-CN/mini-app/develop/server/server-api-introduction) | [./mini-app/../openapi.json](./mini-app/src/main/resources/openapi.json) |
| [移动/网站应用](https://developer.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/list) | [./dop/../openapi.json](./dop/src/main/resources/openapi.json) |
| [生活服务商家应用](https://developer.open-douyin.com/docs/resource/zh-CN/local-life/develop/preparation/signruleintroduce) | [./local-life/../openapi.json](./local-life/src/main/resources/openapi.json) |

## Usage

可根据上述 `openapi.json` 文件，生成相应的 http client 代码，具体用法请见[openapi-generator](https://github.com/OpenAPITools/openapi-generator)

默认已生成 Java client 端的 [openfeign](https://github.com/OpenFeign/feign) 代码

具体依赖请查看 [Maven Central](https://central.sonatype.com/namespace/io.github.eahau.openapi)

- 小程序
```xml
      <dependency>
        <groupId>io.github.eahau.openapi</groupId>
        <artifactId>douyin-mini-app</artifactId>
        <version>${douyin-openapi.verison}</version>
      </dependency>
```  

- 移动/网站应用
```xml
      <dependency>
        <groupId>io.github.eahau.openapi</groupId>
        <artifactId>douyin-dop</artifactId>
        <version>${douyin-openapi.verison}</version>
      </dependency>
``` 

- 生活服务商家应用
```xml
      <dependency>
        <groupId>io.github.eahau.openapi</groupId>
        <artifactId>douyin-local-life</artifactId>
        <version>${douyin-openapi.verison}</version>
      </dependency>
```

## 更新维护
由于 `openapi.json` 是根据抖音 OpenApi 官方文档自动生成，文档格式不统一，难免有遗漏或错误。
如果需要更正文档，烦请在相应模块下的 `openapi-manaual.json` 中维护。
因为在生成 `openapi.json` 前，会以`openapi-manaual.json`为准，对重名的 [path](https://spec.openapis.org/oas/v3.1.0#paths-object) 和 [schema](https://spec.openapis.org/oas/v3.1.0#schemaObject) 进行覆盖。
随后提交 [PR](https://github.com/eahau/douyin-openapi/pulls) 合并，感谢。

## License
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Feahau%2Fdouyin-openapi.svg?type=large)](https://app.fossa.com/projects/git%2Bgithub.com%2Feahau%2Fdouyin-openapi?ref=badge_large)