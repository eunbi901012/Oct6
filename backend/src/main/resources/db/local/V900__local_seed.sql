-- 로컬 프로필 전용 합성 DB fixture. 운영 Flyway 위치에 db/local을 포함하지 않는다.
-- 모든 표시 데이터는 이 DB에서 조회한다. 실제 개인정보·외부 호출·인증 세션 시드 없음.
-- 아래 BCrypt는 Python에서 로컬 libcrypt로 계산한 검증값이며 평문 비밀번호는 저장하지 않는다.
-- use_status/employment_status/assignment_source/assignment_status 및 미승인 기간·승인자는 NULL이다.
-- 인증 구현은 NULL 상태를 비활성·회수·만료로 단정하지 않고 최초 R09 매핑을 사용할 수 있어야 한다.
-- R09 권한은 DB 설정일 뿐 전역 bypass 또는 영구적인 R09 전용 정책의 근거가 아니다.
-- 중복 시 기존 관리 데이터를 덮어쓰지 않는다. 업무 UK를 추가하지 않고 fixture를 조건부 삽입한다.

INSERT INTO role (role_code, role_name, purpose)
VALUES
    ('R01', '교원', '본인 관련 업무를 수행하는 일반 사용자 역할'),
    ('R02', '학과장', '소속 학과 교원 관련 업무를 확인하는 역할'),
    ('R03', '단과대학(원) 행정실', '단과대학 또는 대학원 행정 처리 역할'),
    ('R04', '교수지원과', '기준정보와 평가 관련 행정 관리 역할'),
    ('R05', '산학협력단', '연구비·간접비·지식재산 관련 자료 관리 역할'),
    ('R06', '입학인재관리과', '입학·취업률 관련 자료 관리 역할'),
    ('R07', '실적부서', '담당 실적 자료 관리 역할'),
    ('R08', '점수산출 감사자', '산출 과정과 근거를 조회하는 감사 역할'),
    ('R09', '시스템관리자', '사용자·조직·메뉴·권한·코드 관리를 수행하는 관리자 역할')
ON CONFLICT (role_code) DO NOTHING;

DO $local_seed$
DECLARE
    v_organization_id BIGINT;
    v_personnel_id BIGINT;
    v_account_id BIGINT;
    v_root_id BIGINT;
    v_middle_id BIGINT;
    v_menu_id BIGINT;
    v_code_group_id BIGINT;
    v_middle RECORD;
    v_leaf RECORD;
BEGIN
    SELECT o.organization_id
    INTO v_organization_id
    FROM organization o
    WHERE o.organization_code = 'MOCK-ORG-01'
    ORDER BY o.organization_id
    LIMIT 1;

    IF v_organization_id IS NULL THEN
        INSERT INTO organization (organization_code, organization_name)
        VALUES ('MOCK-ORG-01', '예시 조직')
        RETURNING organization_id INTO v_organization_id;
    END IF;

    SELECT p.personnel_id
    INTO v_personnel_id
    FROM korus_personnel_snapshot p
    WHERE p.employee_number = 'MOCK-EMP-01'
    ORDER BY p.personnel_id
    LIMIT 1;

    IF v_personnel_id IS NULL THEN
        INSERT INTO korus_personnel_snapshot (
            employee_number, person_name, organization_id, last_synced_at
        )
        VALUES ('MOCK-EMP-01', '예시 사용자', v_organization_id, CURRENT_TIMESTAMP)
        RETURNING personnel_id INTO v_personnel_id;
    END IF;

    INSERT INTO organization_user_mapping (personnel_id, organization_id, position_name)
    SELECT v_personnel_id, v_organization_id, '예시 보직'
    WHERE NOT EXISTS (
        SELECT 1
        FROM organization_user_mapping m
        WHERE m.personnel_id = v_personnel_id
            AND m.organization_id = v_organization_id
            AND m.position_name = '예시 보직'
    );

    INSERT INTO user_account (login_id, password_hash, personnel_id)
    VALUES (
        'admin',
        '$2b$12$TDppEZrPdbGuYqCNcBJfH.cGfmZIS1u4Uax5ZUcLx5FWXG9g9WxIu',
        v_personnel_id
    )
    ON CONFLICT (login_id) DO NOTHING;

    SELECT a.account_id
    INTO v_account_id
    FROM user_account a
    WHERE a.login_id = 'admin';

    INSERT INTO user_role (account_id, role_code)
    SELECT v_account_id, 'R09'
    WHERE NOT EXISTS (
        SELECT 1
        FROM user_role ur
        WHERE ur.account_id = v_account_id
            AND ur.role_code = 'R09'
    );

    SELECT m.menu_id
    INTO v_root_id
    FROM menu m
    WHERE m.parent_menu_id IS NULL
        AND m.menu_name = '시스템 관리'
    ORDER BY m.menu_id
    LIMIT 1;

    IF v_root_id IS NULL THEN
        INSERT INTO menu (menu_name, display_order)
        VALUES ('시스템 관리', 1)
        RETURNING menu_id INTO v_root_id;
    END IF;

    INSERT INTO menu_permission (menu_id, role_code, access_allowed)
    SELECT v_root_id, 'R09', TRUE
    WHERE NOT EXISTS (
        SELECT 1
        FROM menu_permission mp
        WHERE mp.menu_id = v_root_id
            AND mp.role_code = 'R09'
            AND mp.organization_id IS NULL
            AND mp.account_id IS NULL
    );

    FOR v_middle IN
        SELECT d.menu_name, d.display_order
        FROM (VALUES
            ('사용자·조직 관리', 1),
            ('역할·권한 관리', 2),
            ('메뉴 관리', 3),
            ('공통코드 관리', 4)
        ) AS d(menu_name, display_order)
    LOOP
        v_middle_id := NULL;
        SELECT m.menu_id
        INTO v_middle_id
        FROM menu m
        WHERE m.parent_menu_id = v_root_id
            AND m.menu_name = v_middle.menu_name
        ORDER BY m.menu_id
        LIMIT 1;

        IF v_middle_id IS NULL THEN
            INSERT INTO menu (parent_menu_id, menu_name, display_order)
            VALUES (v_root_id, v_middle.menu_name, v_middle.display_order)
            RETURNING menu_id INTO v_middle_id;
        END IF;

        INSERT INTO menu_permission (menu_id, role_code, access_allowed)
        SELECT v_middle_id, 'R09', TRUE
        WHERE NOT EXISTS (
            SELECT 1
            FROM menu_permission mp
            WHERE mp.menu_id = v_middle_id
                AND mp.role_code = 'R09'
                AND mp.organization_id IS NULL
                AND mp.account_id IS NULL
        );

        FOR v_leaf IN
            SELECT d.menu_name, d.screen_id, d.url, d.display_order
            FROM (VALUES
                ('사용자·조직 관리', '사용자 관리', 'SCR-USERS', '/admin/users', 1),
                ('사용자·조직 관리', '조직 관리', 'SCR-ORGANIZATIONS', '/admin/organizations', 2),
                ('역할·권한 관리', '역할 관리', 'SCR-ROLES', '/admin/roles', 1),
                ('역할·권한 관리', '사용자 역할 관리', 'SCR-USER-ROLES', '/admin/user-roles', 2),
                ('역할·권한 관리', '메뉴 권한 관리', 'SCR-MENU-PERMISSIONS', '/admin/menu-permissions', 3),
                ('메뉴 관리', '메뉴 구조 관리', 'SCR-MENU-STRUCTURE', '/admin/menu-structure', 1),
                ('메뉴 관리', '메뉴 정보 관리', 'SCR-MENU-INFORMATION', '/admin/menu-information', 2),
                ('공통코드 관리', '코드그룹 관리', 'SCR-CODE-GROUPS', '/admin/code-groups', 1),
                ('공통코드 관리', '상세코드 관리', 'SCR-DETAIL-CODES', '/admin/detail-codes', 2)
            ) AS d(middle_name, menu_name, screen_id, url, display_order)
            WHERE d.middle_name = v_middle.menu_name
        LOOP
            v_menu_id := NULL;
            SELECT m.menu_id
            INTO v_menu_id
            FROM menu m
            WHERE m.parent_menu_id = v_middle_id
                AND m.screen_id = v_leaf.screen_id
            ORDER BY m.menu_id
            LIMIT 1;

            IF v_menu_id IS NULL THEN
                INSERT INTO menu (parent_menu_id, menu_name, screen_id, url, display_order)
                VALUES (v_middle_id, v_leaf.menu_name, v_leaf.screen_id, v_leaf.url, v_leaf.display_order)
                RETURNING menu_id INTO v_menu_id;
            END IF;

            INSERT INTO menu_permission (menu_id, role_code, access_allowed)
            SELECT v_menu_id, 'R09', TRUE
            WHERE NOT EXISTS (
                SELECT 1
                FROM menu_permission mp
                WHERE mp.menu_id = v_menu_id
                    AND mp.role_code = 'R09'
                    AND mp.organization_id IS NULL
                    AND mp.account_id IS NULL
            );
        END LOOP;
    END LOOP;

    SELECT cg.code_group_id
    INTO v_code_group_id
    FROM code_group cg
    WHERE cg.group_id = 'MOCK-GROUP'
    ORDER BY cg.code_group_id
    LIMIT 1;

    IF v_code_group_id IS NULL THEN
        INSERT INTO code_group (group_id, group_name, description, managing_organization_id)
        VALUES ('MOCK-GROUP', '예시 코드그룹', '로컬 합성 검증용 코드그룹', v_organization_id)
        RETURNING code_group_id INTO v_code_group_id;
    END IF;

    INSERT INTO detail_code (code_group_id, code_value, code_name, display_order, additional_attributes)
    SELECT v_code_group_id, 'MOCK-CODE', '예시 코드', 1, '{"fixture_source":"synthetic-local"}'::JSONB
    WHERE NOT EXISTS (
        SELECT 1
        FROM detail_code dc
        WHERE dc.code_group_id = v_code_group_id
            AND dc.code_value = 'MOCK-CODE'
    );
END;
$local_seed$;
