# openapi-douyin

本项目根据 [抖音开放平台](https://developer.open-douyin.com/) 的 OpenAPI 生成符合 [openapi](https://www.openapis.org/) 规范的 json 文件
目前支持以下 api

- [小程序](https://developer.open-douyin.com/docs/resource/zh-CN/mini-app/develop/server/server-api-introduction)
- [移动/网站应用](https://developer.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/list)
- [生活服务商家应用](https://developer.open-douyin.com/docs/resource/zh-CN/local-life/develop/preparation/signruleintroduce)


可根据以下 openapi.json 文件，生成相应的 http client 代码，具体用法请见[openapi-generator](https://github.com/OpenAPITools/openapi-generator)

- [小程序](./mini-app/src/main/resources/openapi.json)
- [移动/网站应用](./dop/src/main/resources/openapi.json)
- [生活服务商家应用](./local-life/src/main/resources/openapi.json)