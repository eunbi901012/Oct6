import { useCallback, useState } from "react";
import { usersApi } from "../api/users";
import { PageHeading } from "../components/PageHeading";
import type { Query, Row, UserItem } from "../api/types";
import { useSession } from "../app/session";
import { AssignmentEditor } from "../components/AssignmentEditor";
import {
  DataTable,
  Message,
  Panel,
  text,
  useAction,
  useList,
} from "../components/common";
import type { Column } from "../components/common";
import { InputField } from "../components/fields";
import type { Field } from "../components/fields";
import { organizationsReference, rolesReference } from "./catalog";

const searchFields: Field[] = [
  { key: "employeeNumber", label: "교번" },
  { key: "personName", label: "성명" },
  { key: "organizationId", label: "소속", reference: organizationsReference },
  { key: "jobGrade", label: "직급" },
  { key: "employmentStatus", label: "재직상태" },
  { key: "roleCode", label: "역할", reference: rolesReference },
  { key: "useStatus", label: "사용여부" },
];
const personnelColumns: Column<UserItem>[] = [
  { label: "교번", value: (row) => row.personnel?.employee_number },
  { label: "성명", value: (row) => row.personnel?.person_name },
  { label: "소속", value: (row) => row.personnel?.organization_id },
  { label: "직급", value: (row) => row.personnel?.job_grade },
  { label: "재직상태", value: (row) => row.personnel?.employment_status },
  {
    label: "역할",
    value: (row) => row.roles.map((role) => role.role_code).join(", "),
  },
  { label: "사용여부", value: (row) => row.account?.use_status },
  {
    label: "보직",
    value: (row) =>
      row.positions.map((position) => position.position_name).join(", "),
  },
  { label: "퇴직일자", value: (row) => row.personnel?.retirement_date },
  { label: "최종 동기화일시", value: (row) => row.personnel?.last_synced_at },
];

export function UsersPage() {
  const { refresh } = useSession();
  const [draftSearch, setDraftSearch] = useState<Record<string, string>>({});
  const [query, setQuery] = useState<Query>({});
  const loader = useCallback(() => usersApi.search(query), [query]);
  const list = useList(loader);
  const [selected, setSelected] = useState<UserItem | null>(null);
  const [useStatus, setUseStatus] = useState("");
  const [initialRoles, setInitialRoles] = useState<Row[]>([]);
  const [roles, setRoles] = useState<Row[]>([]);
  const statusAction = useAction();
  const rolesAction = useAction();
  const select = (row: UserItem) => {
    if (blocked || list.busy) return;
    setSelected(row);
    setUseStatus(row.account?.use_status ?? "");
    setInitialRoles([...row.roles]);
    setRoles(
      row.roles.map((role) => ({
        role_code: role.role_code,
        approver_id: role.approver_id,
        ...(role.valid_start ? { valid_start: role.valid_start } : {}),
        ...(role.valid_end ? { valid_end: role.valid_end } : {}),
        ...(role.assignment_source
          ? { assignment_source: role.assignment_source }
          : {}),
      })),
    );
  };
  const refetchStatus = async () => {
    const updated = await list.reload();
    const row = updated?.find(
      (item) => item.account?.account_id === selected?.account?.account_id,
    );
    if (row) {
      setSelected(row);
      setUseStatus(row.account?.use_status ?? "");
    } else setSelected(null);
    await refresh();
  };
  const refetchRoles = async () => {
    const updated = await list.reload();
    const row = updated?.find(
      (item) => item.account?.account_id === selected?.account?.account_id,
    );
    if (row) {
      setSelected(row);
      setInitialRoles([...row.roles]);
    } else setSelected(null);
    await refresh();
  };
  const blocked =
    statusAction.busy ||
    rolesAction.busy ||
    statusAction.error?.status === 403 ||
    rolesAction.error?.status === 403;
  return (
    <>
      <PageHeading title="사용자 관리" />
      <Panel title="검색조건">
        <form
          onSubmit={(event) => {
            event.preventDefault();
            if (blocked || list.busy) return;
            setSelected(null);
            setQuery({ ...draftSearch });
          }}
        >
          <div className="form-grid">
            {searchFields.map((field) => (
              <InputField
                key={field.key}
                field={field}
                disabled={blocked || list.busy}
                value={draftSearch[field.key] ?? ""}
                onChange={(value) =>
                  setDraftSearch((current) => ({
                    ...current,
                    [field.key]: value,
                  }))
                }
              />
            ))}
          </div>
          <button type="submit" disabled={list.busy || blocked}>
            검색
          </button>
        </form>
      </Panel>
      <Message error={list.error} busy={list.busy} />
      {!selected && (
        <>
          <Message {...statusAction} />
          <Message {...rolesAction} />
        </>
      )}
      {!list.error && !list.busy && (
        <Panel title="목록">
          <DataTable
            rows={list.rows}
            loading={list.busy}
            failed={!!list.error}
            columns={personnelColumns}
            rowKey={(row) =>
              row.account?.account_id ??
              `personnel-${text(row.personnel?.employee_number)}`
            }
            onSelect={select}
            disabled={blocked || list.busy}
            selectedKey={selected?.account?.account_id}
          />
        </Panel>
      )}
      {selected && list.error?.status !== 403 && list.error?.status !== 401 && (
        <Panel title="선택 사용자 상세">
          <h3>KORUS 원천정보 · 읽기 전용</h3>
          <dl className="readonly-grid">
            {personnelColumns
              .filter(
                (column) =>
                  column.label !== "역할" && column.label !== "사용여부",
              )
              .map((column) => (
                <div key={column.label}>
                  <dt>{column.label}</dt>
                  <dd>{text(column.value(selected))}</dd>
                </div>
              ))}
          </dl>
          {!selected.account ? (
            <p>연결된 내부 계정이 없어 로컬 관리가 불가능합니다.</p>
          ) : (
            <>
              <form
                onSubmit={(event) => {
                  event.preventDefault();
                  void statusAction.run(async () => {
                    if (!selected.account)
                      throw new Error("대상 계정이 없습니다.");
                    await usersApi.useStatus(
                      selected.account.account_id,
                      useStatus,
                    );
                    await refetchStatus();
                  });
                }}
              >
                <fieldset disabled={blocked}>
                  <legend>로컬 관리 · 시스템 사용여부</legend>
                  <label>
                    시스템 사용여부
                    <input
                      value={useStatus}
                      onChange={(event) => setUseStatus(event.target.value)}
                    />
                  </label>
                  <button type="submit">사용여부 저장</button>
                  <button
                    type="button"
                    onClick={() =>
                      setUseStatus(selected.account?.use_status ?? "")
                    }
                  >
                    사용여부 취소
                  </button>
                </fieldset>
                <Message {...statusAction} />
              </form>
              <form
                onSubmit={(event) => {
                  event.preventDefault();
                  void rolesAction.run(async () => {
                    if (!roles.length)
                      throw new Error(
                        "하나 이상의 역할이 필요합니다. 회수는 사용자 역할 관리에서 처리하세요.",
                      );
                    if (!selected.account)
                      throw new Error("대상 계정이 없습니다.");
                    await usersApi.roles(selected.account.account_id, roles);
                    await refetchRoles();
                  });
                }}
              >
                <AssignmentEditor
                  initial={initialRoles}
                  onChange={setRoles}
                  disabled={blocked}
                />
                <div className="actions">
                  <button
                    type="submit"
                    disabled={blocked || roles.length === 0}
                  >
                    업무 역할 저장
                  </button>
                  <button
                    type="button"
                    disabled={blocked}
                    onClick={() => setInitialRoles([...selected.roles])}
                  >
                    업무 역할 취소
                  </button>
                </div>
                <Message {...rolesAction} />
              </form>
              <p>
                사용여부와 업무 역할은 독립적으로 저장됩니다. 한 요청의 실패가
                다른 요청의 성공을 취소하지 않습니다.
              </p>
            </>
          )}
        </Panel>
      )}
    </>
  );
}
