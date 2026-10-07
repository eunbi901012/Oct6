import { useCallback, useState } from "react";
import { userRolesApi } from "../api/user-roles";
import { PageHeading } from "../components/PageHeading";
import type { Row, UserRole } from "../api/types";
import { useSession } from "../app/session";
import { AssignmentEditor } from "../components/AssignmentEditor";
import {
  DataTable,
  Message,
  Panel,
  useAction,
  useList,
} from "../components/common";
import {
  InputField,
  ReferenceSelect,
  toBody,
  toDraft,
} from "../components/fields";
import { assignmentFields, usersReference } from "./catalog";

export function UserRolesPage() {
  const { refresh } = useSession();
  const [userId, setUserId] = useState("");
  const [context, setContext] = useState("");
  const loader = useCallback(() => userRolesApi.list(context), [context]);
  const list = useList(loader, !!context);
  const [selected, setSelected] = useState<UserRole | null>(null);
  const [grantInitial, setGrantInitial] = useState<Row[]>([{}]);
  const [grants, setGrants] = useState<Row[]>([{}]);
  const [draft, setDraft] = useState<Record<string, string>>({});
  const action = useAction();
  const refetch = async () => {
    await list.reload();
    setSelected(null);
    setDraft({});
    setGrantInitial([{}]);
    setGrants([{}]);
    await refresh();
  };
  return (
    <>
      <PageHeading title="사용자 역할 관리" />
      <Panel title="검색조건">
        <p>추가 검색조건은 OQ-UI-001 확인 필요.</p>
        <form
          onSubmit={(event) => {
            event.preventDefault();
            if (action.busy || list.busy) return;
            if (context === userId) void list.reload().catch(() => undefined);
            else {
              setSelected(null);
              setDraft({});
              setGrantInitial([{}]);
              setGrants([{}]);
              setContext(userId);
            }
          }}
        >
          <ReferenceSelect
            reference={usersReference}
            label="역할 조회 대상 사용자"
            value={userId}
            onChange={setUserId}
            disabled={action.busy || list.busy}
          />
          <button type="submit" disabled={!userId || list.busy || action.busy}>
            조회
          </button>
        </form>
      </Panel>
      <Message error={list.error} busy={list.busy} />
      {context && list.error?.status !== 403 && list.error?.status !== 401 && (
        <>
          <Panel title="현재 역할 목록">
            <p>선택 사용자 식별자: {context}</p>
            <DataTable
              rows={list.rows}
              loading={list.busy}
              failed={!!list.error}
              columns={[
                { label: "역할", value: (row) => row.role_code },
                { label: "승인자", value: (row) => row.approver_id },
                { label: "유효 시작일", value: (row) => row.valid_start },
                { label: "유효 종료일", value: (row) => row.valid_end },
                {
                  label: "보직/수동 구분",
                  value: (row) => row.assignment_source,
                },
              ]}
              rowKey={(row) => row.assignment_id}
              disabled={action.busy || list.busy || !!list.error}
              selectedKey={selected?.assignment_id}
              onSelect={(row) => {
                if (action.busy || list.busy || list.error) return;
                setSelected(row);
                setDraft(toDraft(assignmentFields, row));
              }}
            />
          </Panel>
          <Panel title="역할 부여 / 변경 / 회수">
            <fieldset
              disabled={
                action.busy || list.busy || action.error?.status === 403
              }
            >
              {!selected ? (
                <form
                  onSubmit={(event) => {
                    event.preventDefault();
                    void action.run(async () => {
                      if (
                        !grants.length ||
                        grants.some((row) => !row.role_code || !row.approver_id)
                      ) {
                        throw new Error(
                          "역할과 승인자를 선택하세요. 승인자는 자동 지정되지 않습니다.",
                        );
                      }
                      await userRolesApi.grant(Number(context), grants);
                      await refetch();
                    });
                  }}
                >
                  <AssignmentEditor
                    initial={grantInitial}
                    onChange={setGrants}
                  />
                  <button type="submit" disabled={!grants.length}>
                    부여 저장
                  </button>
                </form>
              ) : (
                <form
                  onSubmit={(event) => {
                    event.preventDefault();
                    void action.run(async () => {
                      const body = toBody(assignmentFields, draft, false);
                      if (!body.role_code || !body.approver_id)
                        throw new Error("역할과 승인자를 선택하세요.");
                      await userRolesApi.change(selected.assignment_id, body);
                      await refetch();
                    });
                  }}
                >
                  <div className="form-grid">
                    {assignmentFields.map((field) => (
                      <InputField
                        key={field.key}
                        field={field}
                        value={draft[field.key] ?? ""}
                        onChange={(value) =>
                          setDraft((current) => ({
                            ...current,
                            [field.key]: value,
                          }))
                        }
                      />
                    ))}
                  </div>
                  <button type="submit">변경 저장</button>
                  <button
                    type="button"
                    onClick={() => {
                      if (
                        !window.confirm(
                          "선택한 역할을 회수하시겠습니까? 승인자와 기간 기록을 전송합니다.",
                        )
                      )
                        return;
                      void action.run(async () => {
                        const body = toBody(
                          assignmentFields.filter((field) =>
                            [
                              "approver_id",
                              "valid_start",
                              "valid_end",
                            ].includes(field.key),
                          ),
                          draft,
                          false,
                        );
                        if (!body.approver_id)
                          throw new Error("회수 승인자를 선택하세요.");
                        await userRolesApi.revoke(selected.assignment_id, body);
                        await refetch();
                      });
                    }}
                  >
                    회수
                  </button>
                </form>
              )}
              <button
                type="button"
                onClick={() => {
                  if (selected) setDraft(toDraft(assignmentFields, selected));
                  else {
                    setGrantInitial([{}]);
                    setGrants([{}]);
                  }
                }}
              >
                취소
              </button>
              <button
                type="button"
                onClick={() => {
                  setSelected(null);
                  setGrantInitial([{}]);
                  setGrants([{}]);
                }}
              >
                신규 부여
              </button>
            </fieldset>
            <Message {...action} />
            <p>
              보직 자동부여·기간 만료 판정은 OQ-005 미확정이며 서버의 조회값을
              그대로 표시합니다.
            </p>
          </Panel>
        </>
      )}
    </>
  );
}
