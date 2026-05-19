## 3.4 네이버 로그인 연동 개발하기

### 3.4.1 네이버 로그인 연동을 개발하기에 앞서

네이버 로그인을 적용하기 위해서는 먼저 네이버 개발자센터를 통해 애플리케이션을 등록하여야합니다.  
개발자센터의 '내 애플리케이션' 메뉴에서 내가 등록한 애플리케이션의 Client ID와 Client Secret 값을 확인할 수 있습니다.

**ClientID와 ClientSecret에 대하여**

Client ID와 Client Secret은 내 애플리케이션을 구분해주는 중요한 정보입니다. 반드시 안전하게 보관하시기 바랍니다.  
또한 네이버 로그인 연동 과정에서 활용되는 정보입니다. 잘못된 Client ID / Client Secret 정보를 사용하게 되면 연동이 실패할 수 있습니다.  
한 번 발급이 된 Client ID는 변경이 불가능합니다. 하지만 Client Secret 정보는 개발자센터를 통해 재발급 받는것이 가능합니다.  
Client Secret의 유출이 의심되면 재발급을 통해 도용을 방지할 수 있습니다.

**_Client ID와 Client Secret의 규격_**

- Client ID: 알파뱃 대소문자, 숫자가 조합된 40자리 이하의 문자열
- Client Secret: 알파뱃 대소문자, 숫자가 조합된 40자리 이하의 문자열

**API 권한에 대하여**

네이버 로그인을 이용하면 네이버에서 제공하는 로그인 오픈 API를 활용하여 서비스를 개발할 수 있습니다.  
오픈 API를 이용하기 위해서는 애플리케이션에서 API를 호출할수 있도록 권한을 설정하여야합니다.

- API 권한 설정: '내 애플리케이션'의 'API 권한관리' 탭에서 사용하고자 하는 API에 대하여 권한을 설정 할 수 있습니다.

**_API 호출권한이 없을 경우_**

API 권한 설정을 하지 않았거나 네이버 로그인 시 사용자가 권한동의를 하지 않을 경우 API호출이 실패할 수 있습니다.  
이에따라 원활한 API이용을 위해서는 권한 설정을 반드시 체크하여야 합니다.

**등록정보가 올바르지 않을 경우**

만약 개발자센터의 애플리케이션 등록정보에서 서비스 URL 혹은 Callback URL 정보가 올바르지 않게 기입 또는 누락되거나 등록정보와 일치하지 않는 환경에서 네이버 로그인을 시도하는 경우 다음과 같이 로그인 과정에서 오류가 발생할 수 있습니다.  
따라서 서비스 적용 전 반드시 등록정보가 올바르게 적용이 되어있는지 테스트를 수행하여야 합니다.

![img_naverid_32](https://developers.naver.com/proxyapi/rawgit/naver/naver-openapi-guide/master/ko/login/devguide/images/img_naverid_32.png)

### 3.4.2 네이버 로그인 연동 URL 생성하기

네이버 로그인 연동을 진행하기 위해서는 네이버 로그인 버튼을 클릭하였을 때 이동할 '네이버 로그인' URL을 먼저 생성하여야 합니다.  
이 과정에서 사용자는 네이버에 로그인인증을 수행하고 네이버 로그인 연동 동의과정을 수행할 수 있습니다.  
사용자가 로그인 연동에 동의하였을 경우 동의 정보를 포함하여 Callback URL로 전송됩니다.

**_요청 URL 정보_**

| 메서드     | 요청 URL                                 | 출력 포맷      | 설명                    |
| ---------- | ---------------------------------------- | -------------- | ----------------------- |
| GET / POST | https://nid.naver.com/oauth2.0/authorize | URL 리다이렉트 | 네이버 로그인 인증 요청 |

**_요청 변수 정보_**

| 요청 변수명   | 타입   | 필수 여부 | 기본값 | 설명                                                                                                                                       |
| ------------- | ------ | --------- | ------ | ------------------------------------------------------------------------------------------------------------------------------------------ |
| response_type | string | Y         | code   | 인증 과정에 대한 내부 구분값으로 'code'로 전송해야 함                                                                                      |
| client_id     | string | Y         | \-     | 애플리케이션 등록 시 발급받은 Client ID 값                                                                                                 |
| redirect_uri  | string | Y         | \-     | 애플리케이션을 등록 시 입력한 Callback URL 값으로 URL 인코딩을 적용한 값                                                                   |
| state         | string | Y         | \-     | 사이트 간 요청 위조(cross-site request forgery) 공격을 방지하기 위해 애플리케이션에서 생성한 상태 토큰값으로 URL 인코딩을 적용한 값을 사용 |

### 3.4.3 네이버 로그인 연동 결과 Callback 정보

네이버 로그인 인증 요청 API를 호출했을 때 사용자가 네이버로 로그인하지 않은 상태이면 네이버 로그인 화면으로 이동하고, 사용자가 네이버에 로그인한 상태이면 기본 정보 제공 동의 확인 화면으로 이동합니다.  
네이버 로그인과 정보 제공 동의 과정이 완료되면 콜백 URL에 code값과 state 값이 URL 문자열로 전송됩니다. code 값은 접근 토큰 발급 요청에 사용합니다.  
API 요청 실패시에는 에러 코드와 에러 메시지가 전송됩니다.

**_Callback 응답 정보_**

- API 요청 성공시: http://콜백URL/redirect?code={code값}&state={state값}
- API 요청 실패시: http://콜백URL/redirect?state={state값}&error={에러코드값}&error_description={에러메시지}

| 필드              | 타입   | 설명                                                                                                  |
| ----------------- | ------ | ----------------------------------------------------------------------------------------------------- |
| code              | string | 네이버 로그인 인증에 성공하면 반환받는 인증 코드, 접근 토큰(access token) 발급에 사용                 |
| state             | string | 사이트 간 요청 위조 공격을 방지하기 위해 애플리케이션에서 생성한 상태 토큰으로 URL 인코딩을 적용한 값 |
| error             | string | 네이버 로그인 인증에 실패하면 반환받는 에러 코드                                                      |
| error_description | string | 네이버 로그인 인증에 실패하면 반환받는 에러 메시지                                                    |

### 3.4.4 접근 토큰 발급 요청

Callback으로 전달받은 정보를 이용하여 접근 토큰을 발급받을 수 있습니다. 접근 토큰은 사용자가 인증을 완료했다는 것을 보장할 수 있는 인증 정보입니다.  
이 접근 토큰을 이용하여 프로필 API를 호출하거나 오픈API를 호출하는것이 가능합니다.

Callback으로 전달받은 'code' 값을 이용하여 '접근토큰발급API'를 호출하게 되면 API 응답으로 접근토큰에 대한 정보를 받을 수 있습니다.  
'code' 값을 이용한 API호출은 최초 1번만 수행할 수 있으며 접근 토큰 발급이 완료되면 사용된 'code'는 더 이상 재사용할수 없습니다.

**_요청 URL 정보_**

| 메서드     | 요청 URL                             | 출력 포맷 | 설명               |
| ---------- | ------------------------------------ | --------- | ------------------ |
| GET / POST | https://nid.naver.com/oauth2.0/token | json      | 접근토큰 발급 요청 |

**_요청 변수 정보_**

| 요청 변수명      | 타입   | 필수 여부    | 기본값  | 설명                                                                                                                                       |
| ---------------- | ------ | ------------ | ------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| grant_type       | string | Y            | \-      | 인증 과정에 대한 구분값 1) 발급:'authorization_code' 2) 갱신:'refresh_token' 3) 삭제: 'delete'                                             |
| client_id        | string | Y            | \-      | 애플리케이션 등록 시 발급받은 Client ID 값                                                                                                 |
| client_secret    | string | Y            | \-      | 애플리케이션 등록 시 발급받은 Client secret 값                                                                                             |
| code             | string | 발급 때 필수 | \-      | 로그인 인증 요청 API 호출에 성공하고 리턴받은 인증코드값 (authorization code)                                                              |
| state            | string | 발급 때 필수 | \-      | 사이트 간 요청 위조(cross-site request forgery) 공격을 방지하기 위해 애플리케이션에서 생성한 상태 토큰값으로 URL 인코딩을 적용한 값을 사용 |
| refresh_token    | string | 갱신 때 필수 | \-      | 네이버 사용자 인증에 성공하고 발급받은 갱신 토큰(refresh token)                                                                            |
| access_token     | string | 삭제 때 필수 | \-      | 기 발급받은 접근 토큰으로 URL 인코딩을 적용한 값을 사용                                                                                    |
| service_provider | string | 삭제 때 필수 | 'NAVER' | 인증 제공자 이름으로 'NAVER'로 세팅해 전송                                                                                                 |

**_요청문 샘플_**

```
https://nid.naver.com/oauth2.0/token?grant_type=authorization_code&client_id=jyvqXeaVOVmV&client_secret=527300A0_COq1_XV33cf&code=EIc5bFrl4RibFls1&state=9kgsGTfH4j7IyAkg
```

**_응답 정보_**

| 필드              | 타입    | 설명                                                                     |
| ----------------- | ------- | ------------------------------------------------------------------------ |
| access_token      | string  | 접근 토큰, 발급 후 expires_in 파라미터에 설정된 시간(초)이 지나면 만료됨 |
| refresh_token     | string  | 갱신 토큰, 접근 토큰이 만료될 경우 접근 토큰을 다시 발급받을 때 사용     |
| token_type        | string  | 접근 토큰의 타입으로 Bearer와 MAC의 두 가지를 지원                       |
| expires_in        | integer | 접근 토큰의 유효 기간(초 단위)                                           |
| error             | string  | 에러 코드                                                                |
| error_description | string  | 에러 메시지                                                              |

### 3.4.5 접근 토큰을 이용하여 프로필 API 호출하기

접근 토큰을 이용하면 프로필 정보 조회 API를 호출하거나 오픈 API를 호출하는것이 가능합니다.  
사용자 로그인 정보를 획득하기 위해서는 프로필 정보 조회 API를 먼저 호출하여야 합니다.

**_요청 URL 정보_**

| 메서드     | 인증     | 요청 URL                            | 출력 포맷 | 설명             |
| ---------- | -------- | ----------------------------------- | --------- | ---------------- |
| GET / POST | OAuth2.0 | https://openapi.naver.com/v1/nid/me | JSON      | 프로필 정보 조회 |

**_요청 변수 정보_**

요청 변수는 별도로 없으며, 요청 URL로 호출할 때 아래와 같이 요청 헤더에 접근 토큰 값을 전달하면 됩니다.

**_요청 헤더_**

| 요청 헤더명   | 설명                                                                                                                                                                                                 |
| ------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Authorization | 접근 토큰(access token)을 전달하는 헤더 다음과 같은 형식으로 헤더 값에 접근 토큰(access token)을 포함합니다. 토큰 타입은 "Bearer"로 값이 고정되어 있습니다. Authorization: {토큰 타입\] {접근 토큰\] |

**_출력 결과_**

| 필드                   | 타입   | 필수 여부 | 설명                                                                              |
| ---------------------- | ------ | --------- | --------------------------------------------------------------------------------- |
| resultcode             | String | Y         | API 호출 결과 코드                                                                |
| message                | String | Y         | 호출 결과 메시지                                                                  |
| response/id            | String | Y         | 동일인 식별 정보 동일인 식별 정보는 네이버 아이디마다 고유하게 발급되는 값입니다. |
| response/nickname      | String | Y         | 사용자 별명                                                                       |
| response/name          | String | Y         | 사용자 이름                                                                       |
| response/email         | String | Y         | 사용자 메일 주소                                                                  |
| response/gender        | String | Y         | 성별 \- F: 여성 \- M: 남성 \- U: 확인불가                                         |
| response/age           | String | Y         | 사용자 연령대                                                                     |
| response/birthday      | String | Y         | 사용자 생일(MM-DD 형식)                                                           |
| response/profile_image | String | Y         | 사용자 프로필 사진 URL                                                            |
| response/birthyear     | String | Y         | 출생연도                                                                          |
| response/mobile        | String | Y         | 휴대전화번호                                                                      |

### 3.4.6 접근 토큰을 이용하여 사용자 허용 프로필 권한 확인하기

접근 토큰을 이용하여 사용자가 제공을 허용한 프로필의 항목을 확인하는것이 가능합니다.  
특정 사용자 프로필 항목이 서비스 운영에 필수적으로 필요한 경우 프로필 조회에 앞서 먼저 제공 항목을 확인하는것이 좋습니다.

**_요청 URL 정보_**

| 메서드     | 인증     | 요청 URL                                | 출력 포맷 | 설명                        |
| ---------- | -------- | --------------------------------------- | --------- | --------------------------- |
| GET / POST | OAuth2.0 | https://openapi.naver.com/v1/nid/verify | JSON      | 접근 토큰 검증 및 권한 확인 |

**_요청 변수 정보_**

| 요청 변수명 | 타입    | 필수 여부 | 기본값 | 설명                           |
| ----------- | ------- | --------- | ------ | ------------------------------ |
| info        | boolean | N         | false  | true일 경우 권한 설정정보 응답 |

**_요청 헤더_**

| 요청 헤더명   | 설명                                                                                                                                                                                                 |
| ------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Authorization | 접근 토큰(access token)을 전달하는 헤더 다음과 같은 형식으로 헤더 값에 접근 토큰(access token)을 포함합니다. 토큰 타입은 "Bearer"로 값이 고정되어 있습니다. Authorization: {토큰 타입\] {접근 토큰\] |

**_출력 결과_**

| 필드                     | 타입   | 필수 여부 | 설명                          |
| ------------------------ | ------ | --------- | ----------------------------- |
| resultcode               | String | Y         | API 호출 결과 코드            |
| message                  | String | Y         | 호출 결과 메시지              |
| response/token           | String | Y         | 접근토큰                      |
| response/expire_date     | String | Y         | 접근토큰만료시각              |
| response/allowed_profile | String | Y         | 허용 프로필 항목(쉼표로 구분) |

## 3.5 Open ID Connect로 네이버 로그인 연동하기

### 3.5.1 개발하기에 앞서

기존에 제공하고 있는 OAuth2.0 API와 별도로 분리하여 기능을 제공하고 있는 형태입니다. 다음 설명에서 기존 API와 유사하지만 다른 path로 제공하고 있는 점과 요청 파라메터와 응답값이 유사하지만 일부 다른 형태를 띄고있음을 유의하시기 바랍니다.

Open ID Connect(ODIC)를 사용하여 id_token 발급을 추가로 받는 경우 기존과 다른 API 사용이 필요합니다. 본문에서 앞으로 Open ID Connect는 OIDC로 표기합니다.

### 3.5.2 OIDC configuration 정보 조회

OIDC에서 제공하고 있는 API와 메타 정보 조회를 위한 API입니다. OIDC를 구현한 client side framework를 사용하는 경우 해당 URL을 설정하여 손쉽게 OIDC를 적용할 수 있습니다.

**_요청 URL 정보_**

| 메서드 | 요청 URL                                               | 출력 포맷 | 설명           |
| ------ | ------------------------------------------------------ | --------- | -------------- |
| GET    | https://nid.naver.com/.well-known/openid-configuration | JSON      | OIDC 메타 정보 |

**_요청문 샘플_**

```
https://nid.naver.com/.well-known/openid-configuration
```

### 3.5.3 jwk key 발급

id_token 생성과 시그니처 검증에 사용될 key를 발급 하는 API. 초기화 과정에서 OIDC용 jwk key가 존재 하지 않는다면 요청하여 key를 설정합니다.

**_요청 URL 정보_**

| 메서드 | 요청 URL                          | 출력 포맷 | 설명                                   |
| ------ | --------------------------------- | --------- | -------------------------------------- |
| GET    | https://nid.naver.com/oauth2/jwks | JSON      | id_token 발급, 검증시에 사용할 키 정보 |

**_요청문 샘플_**

```
https://nid.naver.com/oauth2/jwks
```

### 3.5.4 네이버 로그인 연동 URL 생성하기

3.4.2 내용과 동일

**_요청 URL 정보_**

| 메서드     | 요청 URL                               | 출력 포맷      | 설명                    |
| ---------- | -------------------------------------- | -------------- | ----------------------- |
| GET / POST | https://nid.naver.com/oauth2/authorize | URL 리다이렉트 | 네이버 로그인 인증 요청 |

**_요청 변수 정보_**

| 요청 변수명           | 타입   | 필수 여부 | 기본값 | 설명                                                                                                                                       |
| --------------------- | ------ | --------- | ------ | ------------------------------------------------------------------------------------------------------------------------------------------ |
| response_type         | string | Y         | code   | 인증 과정에 대한 내부 구분값으로 'code'로 전송해야 함                                                                                      |
| client_id             | string | Y         | \-     | 애플리케이션 등록 시 발급받은 Client ID 값                                                                                                 |
| redirect_uri          | string | Y         | \-     | 애플리케이션을 등록 시 입력한 Callback URL 값으로 URL 인코딩을 적용한 값                                                                   |
| state                 | string | Y         | \-     | 사이트 간 요청 위조(cross-site request forgery) 공격을 방지하기 위해 애플리케이션에서 생성한 상태 토큰값으로 URL 인코딩을 적용한 값을 사용 |
| scope                 | string | Y         | \-     | 'openid' scope 필수                                                                                                                        |
| code_challenge        | string | N         | \-     | 해시처리된 PKCE value                                                                                                                      |
| code_challenge_method | string | N         | S256   | PKCE 알고리즘                                                                                                                              |

### 3.5.5 네이버 로그인 연동 결과 Callback 정보

네이버 로그인 인증 요청 API를 호출했을 때 사용자가 네이버로 로그인하지 않은 상태이면 네이버 로그인 화면으로 이동하고, 사용자가 네이버에 로그인한 상태이면 기본 정보 제공 동의 확인 화면으로 이동합니다.  
네이버 로그인과 정보 제공 동의 과정이 완료되면 콜백 URL에 code값과 state 값이 URL 문자열로 전송됩니다. code 값은 접근 토큰 발급 요청에 사용합니다.  
API 요청 실패시에는 에러 코드와 에러 메시지가 전송됩니다.

**_Callback 응답 정보_**

- API 요청 성공시: http://콜백URL/redirect?code={code값}&state={state값}
- API 요청 실패시: http://콜백URL/redirect?state={state값}&error={에러코드값}&error_description={에러메시지}

| 필드              | 타입   | 설명                                                                                                  |
| ----------------- | ------ | ----------------------------------------------------------------------------------------------------- |
| code              | string | 네이버 로그인 인증에 성공하면 반환받는 인증 코드, 접근 토큰(access token) 발급에 사용                 |
| state             | string | 사이트 간 요청 위조 공격을 방지하기 위해 애플리케이션에서 생성한 상태 토큰으로 URL 인코딩을 적용한 값 |
| error             | string | 네이버 로그인 인증에 실패하면 반환받는 에러 코드                                                      |
| error_description | string | 네이버 로그인 인증에 실패하면 반환받는 에러 메시지                                                    |

### 3.5.6 접근 토큰 발급 요청

Callback으로 전달받은 정보를 이용하여 접근 토큰을 발급받을 수 있습니다. 접근 토큰은 사용자가 인증을 완료했다는 것을 보장할 수 있는 인증 정보입니다.  
이 접근 토큰을 이용하여 프로필 API를 호출하거나 오픈API를 호출하는것이 가능합니다.

Callback으로 전달받은 'code' 값을 이용하여 '접근토큰발급API'를 호출하게 되면 API 응답으로 접근토큰에 대한 정보를 받을 수 있습니다.  
'code' 값을 이용한 API호출은 최초 1번만 수행할 수 있으며 접근 토큰 발급이 완료되면 사용된 'code'는 더 이상 재사용할수 없습니다.

**_요청 URL 정보_**

| 메서드 | 요청 URL                           | 출력 포맷 | 설명               |
| ------ | ---------------------------------- | --------- | ------------------ |
| POST   | https://nid.naver.com/oauth2/token | json      | 접근토큰 발급 요청 |

**_요청 변수 정보_**

| 요청 변수명      | 타입   | 필수 여부    | 기본값  | 설명                                                                                                                                       |
| ---------------- | ------ | ------------ | ------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| grant_type       | string | Y            | \-      | 인증 과정에 대한 구분값 1) 발급:'authorization_code' 2) 갱신:'refresh_token' 3) 삭제: 'delete'                                             |
| client_id        | string | Y            | \-      | 애플리케이션 등록 시 발급받은 Client ID 값                                                                                                 |
| client_secret    | string | Y            | \-      | 애플리케이션 등록 시 발급받은 Client secret 값                                                                                             |
| code             | string | 발급 때 필수 | \-      | 로그인 인증 요청 API 호출에 성공하고 리턴받은 인증코드값 (authorization code)                                                              |
| state            | string | 발급 때 필수 | \-      | 사이트 간 요청 위조(cross-site request forgery) 공격을 방지하기 위해 애플리케이션에서 생성한 상태 토큰값으로 URL 인코딩을 적용한 값을 사용 |
| refresh_token    | string | 갱신 때 필수 | \-      | 네이버 사용자 인증에 성공하고 발급받은 갱신 토큰(refresh token)                                                                            |
| access_token     | string | 삭제 때 필수 | \-      | 기 발급받은 접근 토큰으로 URL 인코딩을 적용한 값을 사용                                                                                    |
| service_provider | string | 삭제 때 필수 | 'NAVER' | 인증 제공자 이름으로 'NAVER'로 세팅해 전송                                                                                                 |
| code_verifier    | string | N            | \-      | PKCE로 동작하는 경우 추가                                                                                                                  |

**_응답 정보_**

| 필드              | 타입    | 설명                                                                     |
| ----------------- | ------- | ------------------------------------------------------------------------ |
| access_token      | string  | 접근 토큰, 발급 후 expires_in 파라미터에 설정된 시간(초)이 지나면 만료됨 |
| refresh_token     | string  | 갱신 토큰, 접근 토큰이 만료될 경우 접근 토큰을 다시 발급받을 때 사용     |
| token_type        | string  | 접근 토큰의 타입으로 Bearer와 MAC의 두 가지를 지원                       |
| expires_in        | integer | 접근 토큰의 유효 기간(초 단위)                                           |
| id_token          | integer | id token, 사용자 인증시 사용                                             |
| error             | string  | 에러 코드                                                                |
| error_description | string  | 에러 메시지                                                              |

## 4\. 네이버 로그인 사용자 프로필 갱신 및 재인증

## 4.1 네이버 로그인 사용자의 프로필 갱신

### 4.1.1 접근 토큰에 대하여

접근 토큰 발급 API를 통하여 접근 토큰 및 갱신 토큰을 발급받을수 있습니다.  
접근 토큰은 다음과 같은 형식으로 이루어져 있습니다.

**_접근 토큰 API 응답형태_**

```json
{
  "access_token": "접근토큰(Access Token)",
  "refresh_token": "갱신토큰(Refresh Token)",
  "token_type": "접근토큰 타입(bearer)",
  "expires_in": "유효시간(초)"
}
```

**_접근 토큰 규격_**

접근 토큰은 아래와 같은 규격을 지니고 있습니다.

- access_token: 알파벳 대소문자, 숫자, 특수문자( +/= )가 조합된 256자리 이하의 문자열
- refresh_token: 알파벳 대소문자, 숫자가 조합된 256자리 이하의 문자열
- expires_in: 숫자, 발급 시점부터 expires_in(초) 후 까지 유효

**_접근 토큰의 용도_**

접근 토큰은 사용자 프로필 조회 API를 호출하거나 네이버에서 제공하는 로그인 OpenAPI를 이용할때 사용자 인증값으로 이용됩니다.

**_접근 토큰 사용 방법_**

접근 토큰을 이용하여 API를 호출하는 경우 다음과 같이 요청 헤더에 접근 토큰 값을 포함합니다.

- 요청 헤더명: Authorization
- 요청 헤더값 형식: TOKEN_TYPE ACCESS_TOKEN

**_접근 토큰을 포함한 응답헤더 예시_**

```
Authorization: Bearer ACCESS_TOKEN
```

**_접근 토큰을 이용한 API호출 예시_**

```
curl -XGET "https://openapi.naver.com/v1/nid/me" \
     -H "Authorization: Bearer ACCESS_TOKEN"

GET /v1/nid/me HTTP/1.1
Host: openapi.naver.com
User-Agent: curl/7.43.0
Accept: */*
Authorization: Bearer ACCESS_TOKEN
```

### 4.1.2 갱신 토큰에 대하여

접근 토큰이 만료가 된 경우 접근 토큰과 함께 발급받은 갱신 토큰 (refresh token)을 이용하여 유효한 접근토큰을 재발급 받을 수 있습니다.  
그렇기 때문에 갱신 토큰은 접근 토큰이 만료될 것을 대비하여 데이터베이스에 별도로 저장하고 이후 필요에 따라 갱신 토큰을 사용하면 됩니다.

갱신 토큰을 이용하여 접근 토큰을 재발급받는 방법은 아래와 같습니다.

**_요청 URL 정보_**

| 메서드     | 요청 URL                             | 출력 포맷 | 설명                                   |
| ---------- | ------------------------------------ | --------- | -------------------------------------- |
| GET / POST | https://nid.naver.com/oauth2.0/token | JSON      | 갱신토큰을 이용한 접근토큰 재발급 요청 |

**_요청 변수 정보_**

| 요청 변수명   | 타입   | 필수 여부 | 기본값 | 설명                                           |
| ------------- | ------ | --------- | ------ | ---------------------------------------------- |
| client_id     | string | Y         | \-     | 애플리케이션 등록 시 발급받은 Client ID 값     |
| client_secret | string | Y         | \-     | 애플리케이션 등록 시 발급받은 Client Secret 값 |
| refresh_token | string | Y         | \-     | 접근토큰 발급API를 통하여 발급받은 갱신토큰 값 |
| grant_type    | string | Y         | \-     | 요청 타입. refresh_token 으로 설정             |

**_요청문 샘플_**

```
https://nid.naver.com/oauth2.0/token?grant_type=refresh_token&client_id=CLIENT_ID&client_secret=CLIENT_SECRET&refresh_token=REFRESH_TOKEN
```

**_출력 결과_**

| 필드         | 타입   | 필수 여부 | 설명                             |
| ------------ | ------ | --------- | -------------------------------- |
| access_token | String | Y         | 재발급 받은 접근토큰             |
| token_type   | String | Y         | 토큰 타입 (bearer)               |
| expires_in   | String | Y         | 접근토큰 유효성 체크 결과 메시지 |

### 4.1.3 접근 토큰 만료와 갱신 주기. 프로필 정보의 갱신

접근 토큰은 만료일자에 따라 또는 접근 토큰 갱신, 삭제 등의 동작에 따라 유효하지 않게 될 수 있습니다.  
유효하지 않은 접근 토큰으로는 프로필 정보를 조회하거나 로그인 OpenAPI를 호출할 수 없습니다.  
따라서 접근 토큰이 유효하지 않은 경우에는 갱신토큰을 이용하여 유효한 접근 토큰으로 재발급 받거나 네이버 로그인 인증을 다시한번 수행하는것으로 유효한 접근 토큰을 발급받을 수 있습니다.

접근 토큰의 유효성을 판단하기 위해서는 다음과 같은 방법을 이용할 수 있습니다.

- 프로필 정보 조회 API 호출 시 응답이 정상적으로 전달될 경우 접근 토큰은 유효하다고 할 수 있습니다.
- 접근 토큰 유효성 체크 API 호출을 통해 현재 접근 토큰이 유효한지 판단할 수 있습니다.

접근 토큰 유효성 체크 API는 다음과 같이 이용 가능합니다.

**_요청 URL 정보_**

| 메서드     | 인증     | 요청 URL                            | 출력 포맷 | 설명                  |
| ---------- | -------- | ----------------------------------- | --------- | --------------------- |
| GET / POST | OAuth2.0 | https://openapi.naver.com/v1/nid/me | JSON      | 접근 토큰 유효성 체크 |

**_요청 변수 정보_**

요청 변수는 별도로 없으며, 요청 URL로 호출할 때 아래와 같이 요청 헤더에 접근 토큰 값을 전달하면 됩니다.

**_요청 헤더_**

| 요청 헤더명   | 설명                                                                                                                                                                                              |
| ------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Authorization | 접근 토큰(access token)을 전달하는 헤더다음과 같은 형식으로 헤더 값에 접근 토큰(access token)을 포함합니다. 토큰 타입은 "Bearer"로 값이 고정돼 있습니다. Authorization: {토큰 타입\] {접근 토큰\] |

**_출력 결과_**

| 필드       | 타입   | 필수 여부 | 설명                             |
| ---------- | ------ | --------- | -------------------------------- |
| resultcode | String | Y         | API 호출 결과 코드               |
| message    | String | Y         | 접근토큰 유효성 체크 결과 메시지 |

### 4.1.5 사용자가 거부한 프로필 권한에 대하여 다시 동의를 수행하는 경우

사용자는 네이버 로그인 최초 연동 동의과정에서 특정 프로필 항목에 대하여 **_제공하지않음_** 으로 선택할 수 있습니다. 이러한 경우 제공이 거부된 프로필 항목에 대해서는 프로필 조회로 정보를 얻을 수 없습니다.  
제공이 거부된 프로필 항목이 서비스 이용에 반드시 필요한 항목일 경우에는 사용자로 하여금 다시한번 동의로 선택하도록 **재동의** 를 수행하는것이 가능합니다.

네이버 로그인 재동의 API 명세는 다음과 같습니다.

**_요청 URL 정보_**

| 메서드     | 요청 URL                                 | 출력 포맷      | 설명                    |
| ---------- | ---------------------------------------- | -------------- | ----------------------- |
| GET / POST | https://nid.naver.com/oauth2.0/authorize | URL 리다이렉트 | 네이버 로그인 인증 요청 |

**_요청 변수 정보_**

| 요청 변수명   | 타입   | 필수 여부 | 기본값 | 설명                                                                                                                                       |
| ------------- | ------ | --------- | ------ | ------------------------------------------------------------------------------------------------------------------------------------------ |
| response_type | string | Y         | code   | 인증 과정에 대한 내부 구분값으로 'code'로 전송해야 함                                                                                      |
| client_id     | string | Y         | \-     | 애플리케이션 등록 시 발급받은 Client ID 값                                                                                                 |
| redirect_uri  | string | Y         | \-     | 애플리케이션을 등록 시 입력한 Callback URL 값으로 URL 인코딩을 적용한 값                                                                   |
| state         | string | Y         | \-     | 사이트 간 요청 위조(cross-site request forgery) 공격을 방지하기 위해 애플리케이션에서 생성한 상태 토큰값으로 URL 인코딩을 적용한 값을 사용 |
| auth_type     | string | Y         | \-     | 재동의 요청의 경우 'reprompt' 로 전송해야 함                                                                                               |

**_요청문 샘플_**

```
https://nid.naver.com/oauth2.0/authorize?response_type=code&client_id=CLIENT_ID&state=STATE_STRING&redirect_uri=CALLBACK_URL&auth_type=reprompt
```

위의 동작은 사용자가 권한을 거부한 경우에 대하여 다시한번 확인을 요청하는것이기 때문에 서비스에서 우선적으로 **_해당 항목이 반드시 필요한 사유_** 에 대해 충분히 고지하고 인증을 요청하시기 바랍니다.

## 4.2 재인증

### 4.2.1 사용자 재인증이 필요한 경우

접근 토큰이 유효하더라도 사용자로 하여금 다시한번 인증을 수행하여 계정보안 수준을 높이고자 할때, 네이버 로그인 재인증을 통해서 네이버 사용자 인증을 수행할수 있습니다.

일반적으로 다음과 같은 상황에서 새로이 사용자 인증을 요구할 수 있습니다.

- 사용자의 개인정보 조회 또는 변경 페이지에 접근하는 경우
- 사용자의 서비스 해지 또는 탈퇴를 수행하는 경우
- 사용자 계정 도용이 의심이 되어 사용자 확인이 필요한 경우

네이버 로그인 재인증은 다음과 같은 절차로 수행됩니다.

1. 네이버 로그인 재 인증 요청
2. 현재 로그인 상태와 관계없이 네이버 로그인 절차 요구
3. ID/PW입력
4. 인증 완료

네이버 로그인 재인증 API 명세는 다음과 같습니다.

**_요청 URL 정보_**

| 메서드     | 요청 URL                                 | 출력 포맷      | 설명                         |
| ---------- | ---------------------------------------- | -------------- | ---------------------------- |
| GET / POST | https://nid.naver.com/oauth2.0/authorize | URL 리다이렉트 | 네이버 로그인 인증 요청      |
| GET / POST | https://nid.naver.com/oauth2/authorize   | URL 리다이렉트 | OIDC 네이버 로그인 인증 요청 |

**_요청 변수 정보_**

| 요청 변수명   | 타입   | 필수 여부 | 기본값 | 설명                                                                                                                                       |
| ------------- | ------ | --------- | ------ | ------------------------------------------------------------------------------------------------------------------------------------------ |
| response_type | string | Y         | code   | 인증 과정에 대한 내부 구분값으로 'code'로 전송해야 함                                                                                      |
| client_id     | string | Y         | \-     | 애플리케이션 등록 시 발급받은 Client ID 값                                                                                                 |
| redirect_uri  | string | Y         | \-     | 애플리케이션을 등록 시 입력한 Callback URL 값으로 URL 인코딩을 적용한 값                                                                   |
| state         | string | Y         | \-     | 사이트 간 요청 위조(cross-site request forgery) 공격을 방지하기 위해 애플리케이션에서 생성한 상태 토큰값으로 URL 인코딩을 적용한 값을 사용 |
| auth_type     | string | Y         | \-     | 재인증 요청의 경우 'reauthenticate' 로 전송해야 함                                                                                         |

## 4.3 네이버 Token Revocation(토큰 폐기)

### 4.3.1 네이버 Token Revocation(토큰 폐기)이 필요한 경우

- 기존에는 '네이버 로그인 연동 해제'로 안내하였으나, OAuth 2.0 프로토콜의 표준 용어인 Token Revocation(토큰 폐기)로 변경함

사용자가 서비스를 더이상 이용하지 않거나 (서비스 탈퇴) 네이버 로그인의 연동을 더이상 이용하지 않을 경우 (연동 해제)  
네이버 로그인 연동 해제 API(Token Revocation API)를 통해 연결 관계를 끊을 수 있습니다.

연동 해제 API를 성공적으로 호출하면 다음과 같은 변경사항이 적용됩니다.

- 앞서 발급받은 액세스 토큰(Access Token)과 갱신 토큰(Refresh Token)은 API호출 즉시 OAuth 2.0 RFC 7009 Token Revocation 규격에 따라 즉시 만료되며, 더 이상 사용할 수 없습니다.
- 네이버의 "내정보 > 연결된 서비스 관리"에서 해당 서비스의 로그인 연동 항목이 삭제됩니다.
- 연동 해제 이후 사용자는 다시 연동을 수행할 수 있으며, 연동 과정에서 새로운 사용자 동의 절차가 진행됩니다.

Token Revocation(토큰폐기)는 아래와 같이 이용할 수 있습니다.

**_요청 URL 정보_**

| 메서드     | 요청 URL                             | 출력 포맷 | 설명                            |
| ---------- | ------------------------------------ | --------- | ------------------------------- |
| GET / POST | https://nid.naver.com/oauth2.0/token | JSON      | 접근토큰을 이용한 연결해제 요청 |

**_요청 변수 정보_**

| 요청 변수명   | 타입   | 필수 여부 | 기본값 | 설명                                           |
| ------------- | ------ | --------- | ------ | ---------------------------------------------- |
| client_id     | string | Y         | \-     | 애플리케이션 등록 시 발급받은 Client ID 값     |
| client_secret | string | Y         | \-     | 애플리케이션 등록 시 발급받은 Client Secret 값 |
| access_token  | string | Y         | \-     | 유효한 접근토큰 값                             |
| grant_type    | string | Y         | \-     | 요청 타입. delete 으로 설정                    |

**_출력 결과_**

| 필드         | 타입   | 필수 여부 | 설명                |
| ------------ | ------ | --------- | ------------------- |
| access_token | String | Y         | 삭제처리된 접근토큰 |
| result       | String | Y         | 처리결과 (success)  |

**중요**

연동 해제 API에 사용되는 접근토큰은 반드시 유효한 접근토큰을 이용하여야 합니다.(만료된 토큰이나 존재하지 않는 토큰으로 연동해제 불가)  
따라서 연동 해제를 수행하기 전에 접근토큰의 유효성을 점검하고 5.1의 접근토큰 갱신 과정에 따라 접근토큰을 갱신하는것을 권장합니다.

## 4.4 네이버 로그인 연결 끊기 알림 받기

네이버 로그인 연동 사용자가 다음의 유형으로 서비스를 더이상 이용하지 않을때, 서비스에서는 사용자의 연동 해제 상태에 대한 알림을 받을 수 있습니다.

- 네이버의 "내정보 > 연결된 서비스 관리" 의 네이버 로그인 연동 목록에서 연동된 항목에 대해 "서비스 동의 철회"를 수행하는 경우
- 네이버 회원 서비스를 탈퇴하는 경우

연결 끊기 알림은 API의 형태로 발송이 되며, 알림을 받기 위해서는 다음의 사항을 충족해야합니다.

- 네이버 개발자센터 > 애플리케이션 > API 설정 > 연결끊기 Callback URL 항목에 알림을 받을 API의 주소를 설정해야합니다.
- 연결 끊기 API에 대한 처리가 구현이 되어야합니다.

### 4.4.1 네이버 로그인 연결 끊기 알림 API 명세

아래의 규격에 따라 API를 개발하여, 네이버에 제공해야합니다. 네이버는 제공된 URL정보를 통해 API를 호출합니다.

**_요청 URL 정보_**

| 메서드 | 요청 URL                                               | 출력 포맷                 | 설명                         |
| ------ | ------------------------------------------------------ | ------------------------- | ---------------------------- |
| POST   | 네이버 개발자센터의 API설정을 통해 등록된 Callback URL | N/A (204 No Content 처리) | 네이버 로그인 연결 끊기 알림 |

**_요청 Content-Type_**

application/x-www-form-urlencoded

**_요청 변수 정보_**

| 요청 변수명     | 타입   | 필수 여부 | 기본값 | 설명                                                                          |
| --------------- | ------ | --------- | ------ | ----------------------------------------------------------------------------- |
| clientId        | string | Y         | \-     | 애플리케이션 등록 시 발급받은 Client ID 값                                    |
| encryptUniqueId | string | Y         | \-     | 암호화처리된 이용자 고유 식별자. 관련 규격은 Appendix 를 참고하시기 바랍니다. |
| timestamp       | string | Y         | \-     | 요청 시점의 unix epoch time (second)                                          |
| signature       | string | Y         | \-     | 요청 검증을 위한 서명값. 관련 규격은 Appendix 를 참고하시기 바랍니다.         |

**_응답_**

응답의 경우 HTTP Status Code로 표현합니다. 별도의 응답 본문은 설정하지 않습니다.

> **_주의_**: 네이버에서는 실패 요청에 대해서 재시도를 수행하지 않습니다.

**_응답 HTTP Status Code_**

| HTTP Status Code | Status                | 설명                                                        |
| ---------------- | --------------------- | ----------------------------------------------------------- |
| 204              | No Content            | API처리 성공. ResponseBody는 no content로 전송되어야합니다. |
| 400              | BadRequest            | 잘못된 요청                                                 |
| 403              | Forbidden             | 권한이 없는 URL로 접근한 경우                               |
| 404              | Not Found             | URL Resource 가 존재하지 않는 경우                          |
| 50x              | Internal Server Error | 내부 서버 오류로 인한 호출 실패                             |

### 4.4.2 이용자 고유 식별정보 암호화 전송

pass

## 5\. 네이버 로그인 부가 기능

## 5.1 네이버앱에서 서비스 자동로그인 처리

### 5.1.1 서비스 자동로그인이란

네이버 로그인을 통해 서비스를 이용한적이 있는 사용자가 네이버앱에서 서비스를 접근하는 경우, 사용자의 이용편의를 위하여 서비스에 자동으로 로그인된 상태로 전환하는 기능입니다.

다음과 같은 상항에서 사용자의 로그인 과정을 간소화하여 편의를 제공할 수 있습니다.

- 네이버앱에서 검색을 통해 서비스에 접근하는 경우
- 네이버앱 즐겨찾기를 통해 서비스에 접근하는 경우
- 톡톡, 메일 등으로 전달된 링크를 통해 서비스에 접근하는 경우

자동 로그인은 다음과 같은 절차로 수행됩니다.

1. 자동 로그인 처리 가능 환경에 대한 체크
2. 네이버 로그인 연동 URL에 대한 처리
3. Callback 페이지에서 연동 처리 또는 오류 사항에 대한 처리
4. 로그인 완료

### 5.1.2 제약사항

본 기능은 "네이버앱"에서 서비스의 웹페이지를 접근하는 경우에만 수행이 가능한 기능합니다.

### 5.1.3 네이버앱 판별 조건

네이버앱의 경우 특정 형식의 User-Agent를 지니고 있습니다. 따라서, 요청헤더의 User-Agent헤더를 통해 네이버앱 여부를 판별 가능합니다.

**_판별 조건_**

User-Agent 에 다음의 문자열이 포함되는지 확인

`NAVER(inapp; search;`

**_네이버앱 User-Agent 샘플_**

```
Mozilla/5.0 (iPhone; CPU iPhone OS like Mac OS X) AppleWebKit/605.1.15 NAVER(inapp; search; 620; 10.10.2; XR)
```

### 5.1.4 서비스 자동 로그인 명세

6.1.3의 조건에 부합하는 경우, 서비스에서는 302 redirect 처리 또는 javascript location replace 처리 등으로 사용자를 인증페이지로 이동시킵니다.

**_요청 URL 정보_**

| 메서드     | 요청 URL                                 | 출력 포맷      | 설명                    |
| ---------- | ---------------------------------------- | -------------- | ----------------------- |
| GET / POST | https://nid.naver.com/oauth2.0/authorize | URL 리다이렉트 | 네이버 로그인 인증 요청 |

**_요청 변수 정보_**

| 요청 변수명   | 타입   | 필수 여부 | 기본값 | 설명                                                                                                                                       |
| ------------- | ------ | --------- | ------ | ------------------------------------------------------------------------------------------------------------------------------------------ |
| response_type | string | Y         | code   | 인증 과정에 대한 내부 구분값으로 'code'로 전송해야 함                                                                                      |
| client_id     | string | Y         | \-     | 애플리케이션 등록 시 발급받은 Client ID 값                                                                                                 |
| redirect_uri  | string | Y         | \-     | 애플리케이션을 등록 시 입력한 Callback URL 값으로 URL 인코딩을 적용한 값                                                                   |
| state         | string | Y         | \-     | 사이트 간 요청 위조(cross-site request forgery) 공격을 방지하기 위해 애플리케이션에서 생성한 상태 토큰값으로 URL 인코딩을 적용한 값을 사용 |
| auth_type     | string | Y         | \-     | 자동로그인 요청의 경우 'autologin' 로 전송해야 함                                                                                          |

**_요청문 샘플_**

```
https://nid.naver.com/oauth2.0/authorize?response_type=code&client_id=CLIENT_ID&state=STATE_STRING&redirect_uri=CALLBACK_URL&auth_type=autologin
```

요청이 정상적으로 처리되면 recirect_uri (callback url) 로 처리 결과를 포함하여 페이지 Redirect 처리 됩니다. 정상적으로 자동로그인 대상으로 처리가 된 경우, 네이버 로그인 연동 callback 처리와 동일하게 처리하면 됩니다. (access token 발급 처리 후 로그인 처리)

### 5.1.5 오류 상태와 오류 코드 정의

정상적으로 자동로그인을 처리할 수 없는 경우에는 callback 페이지에 오류 코드를 파라미터로 전달합니다.

| 파라미터          | 타입   | 필수 여부 | 설명           |
| ----------------- | ------ | --------- | -------------- |
| error             | string | Y         | 오류 코드      |
| error_description | string | Y         | 오류 코드 상세 |

**_오류 코드 및 메시지 정의_**

| error 파라미터 | error_description 파라미터       | 설명                                     |
| -------------- | -------------------------------- | ---------------------------------------- |
| access_denied  | user not logged in.              | 네이버에 로그인된 상태가 아닙니다.       |
| access_denied  | need user consent.               | 서비스에 연동된 사용자가 아닙니다.       |
| access_denied  | unsupported browser environment. | 네이버앱이 아닌 환경에서 접근하였습니다. |

**_오류 처리 방안_**

자동 로그인 처리 실패에 대한 오류코드를 콜백으로 전달받은 경우, 기존과 동일하게 로그인 버튼을 통해 로그인 할 수 있도록 처리가 필요합니다.

## Java / Spring Boot, OAuth2으로 OIDC 적용시

OIDC를 구현하고 하고 있는 client framework중 spring security의 OAuth2를 사용하는 경우 적용 예 입니다.

#### 3.1. 요구 사항 및 의존성 추가

Java 17 이상, Spring boot 3.x 이상

##### gradle 의존성

```gradle
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'org.springframework.boot:spring-boot-starter-oauth2-client'
```

#### 3.2. Spring Boot 설정

application.yml 또는 application.properties 설정 Spring Security는 설정 파일에서 OIDC 제공자 정보를 읽어 자동으로 연동합니다.

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          naver:  # 제공자 이름 (임의로 지정 가능)
            provider: naver
            client-id: your-naver-client-id
            client-secret: your-naver-client-secret
            scope: openid,profile
            client-authentication-method: client_secret_post
            authorization-grant-type: authorization_code
            redirect-uri: https://example.com/login/oauth2/code/{registrationId} # 개발자센터에 해당 URL 등록 https://example.com/login/oauth2/code/naver
            scope: openid, profile
        provider:
          naver:
            issuer-uri: https://nid.naver.com  # Naver의 Issuer URI
```

기본 설정 Spring Boot는 최소 설정으로 OIDC를 자동 구성합니다. 기본 동작: /login 경로로 접근 시 OIDC 제공자의 로그인 페이지로 리디렉션. 인증 후 기본 리디렉션 경로는 /. 사용자 정의 설정

Spring Boot 3.x (SecurityFilterChain 사용) Spring Boot 3.x에서는 WebSecurityConfigurerAdapter가 제거되었으므로, SecurityFilterChain을 사용합니다:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/public/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .defaultSuccessUrl("/home", true)
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
            );

        return http.build();
    }

    @Bean
    public OAuth2UserService<OidcUserRequest, OidcUser> oAuth2UserService() {
        OidcUserService oidcUserService = new OidcUserService();
        // NaverOAuth2UserService는 사용자가 별도로 구현한 클래스여야 함
        oidcUserService.setOauth2UserService(new NaverOAuth2UserService());
        return oidcUserService;
    }
}
```

access token으로 사용자 정보 요청처리를 하는 NaverOAuth2UserService 클래스를 추가합니다.

```java
public class NaverOAuth2UserService extends DefaultOAuth2UserService {
	private final RestClient restClient = RestClient.create();
	public NaverOAuth2UserService() {
		super();
	}
	@Override
	public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
		if (userRequest.getClientRegistration().getRegistrationId().equals("naver")) {
			String userInfoUri = userRequest.getClientRegistration()
				.getProviderDetails()
				.getUserInfoEndpoint()
				.getUri();
			String tokenValue = userRequest.getAccessToken()
				.getTokenValue();
			ResponseEntity response = restClient.get()
				.uri(userInfoUri)
				.headers(httpHeaders -> httpHeaders.setBearerAuth(tokenValue))
				.retrieve()
				.toEntity(NaverUserInfoDto.class);
			if (response.hasBody()) {
				NaverUserInfoDto oauthResponse = response.getBody();
                if (oauthResponse != null && oauthResponse.response() instanceof Map userAttributes) {
					userAttributes.computeIfAbsent(StandardClaimNames.SUB, key -> userAttributes.get("id"));
					userAttributes.remove("id");
					Set authorities = new LinkedHashSet<>();
					authorities.add(new OAuth2UserAuthority(userAttributes));
					OAuth2AccessToken token = userRequest.getAccessToken();
					for (String authority : token.getScopes()) {
						authorities.add(new SimpleGrantedAuthority("SCOPE_" + authority));
					}
					return new DefaultOAuth2User(authorities, userAttributes, "name");
				}
			}
		}
		return super.loadUser(userRequest);
	}
	private record NaverUserInfoDto(
		String resultcode,
		String message,
		Map response) {
	}
}
```

#### 3.3. 동작 확인

애플리케이션을 실행합니다. 브라우저에서 http://localhost:8080에 접속하면 /login으로 리디렉션되고, 네이버 로그인 페이지로 이동합니다. 로그인 후 /home으로 리디렉션되며, 인증된 사용자 정보는 SecurityContextHolder에서 확인 가능합니다.

**사용자 정보 확인 예시**

```java
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {
	@GetMapping("/home")
	public String home(@AuthenticationPrincipal OidcUser user) {
	return "Welcome, " + user.getSubject() + ", profile : " + user.getProfile();
	}
}
```
