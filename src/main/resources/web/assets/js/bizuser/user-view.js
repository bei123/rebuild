/*!
Copyright (c) REBUILD <https://getrebuild.com/> and/or its owners. All rights reserved.

rebuild is dual-licensed under commercial and open source licenses (GPLv3).
See LICENSE and COMMERCIAL in the project root for license information.
*/

const wpc = window.__PageConfig || {}
const userId = wpc.recordId

$(document).ready(() => {
  $('.J_delete')
    .off('click')
    .on('click', () => {
      $.get(`/admin/bizuser/delete-checks?id=${userId}`, (res) => {
        if (res.data.hasMember === 0) {
          RbAlert.create($L('此用户可以被安全的删除'), $L('删除用户'), {
            icon: 'alert-circle-o',
            type: 'danger',
            confirmText: $L('删除'),
            confirm: function () {
              deleteUser(userId, this)
            },
          })
        } else {
          RbAlert.create($L('此用户已被使用过，因此不能删除。如不再使用可将其禁用'), $L('无法删除'), {
            icon: 'alert-circle-o',
            type: 'danger',
            confirmText: $L('禁用'),
            confirm: function () {
              toggleDisabled(true, this)
            },
          })
        }
      })
    })

  $('.J_disable').on('click', () => {
    RbAlert.create($L('确认要禁用此用户吗？'), {
      confirmText: $L('禁用'),
      confirm: function () {
        toggleDisabled(true, this)
      },
    })
  })

  $('.J_enable').on('click', () => toggleDisabled(false))

  $('.J_resetpwd').on('click', () => {
    const newpwd = $random(null, true, 6) + 'Rb!8'
    const msg = (
      <RF>
        <div>{WrapHtml($L('密码将重置为 **%s** 是否确认？', newpwd))}</div>
        <div className="mt-2">
          <label className="custom-control custom-control-sm custom-checkbox custom-control-inline mb-2">
            <input className="custom-control-input" type="checkbox" defaultChecked />
            <span className="custom-control-label">
              {$L('发送邮件通知')}
              {!$isTrue(window.__PageConfig.serviceMail) && <span className="fs-12 text-danger ml-1">({$L('不可用')})</span>}
            </span>
          </label>
        </div>
      </RF>
    )

    RbAlert.create(msg, {
      onConfirm: function () {
        const email = $val($(this._element).find('.modal-body input'))
        this.disabled(true)
        $.post(`/admin/bizuser/user-resetpwd?id=${userId}&newp=${$decode(newpwd)}&email=${email}`, (res) => {
          if (res.error_code === 0) {
            RbHighbar.success($L('重置密码成功'))
            this.hide()
          } else {
            RbHighbar.error(res.error_code)
            this.disabled()
          }
        })
      },
      onRendered: function () {
        const $b = $(this._element).find('.modal-body b').addClass('newpwd')
        $clipboard($b, newpwd)
      },
    })
  })

  if (rb.isAdminVerified) {
    $.get(`/admin/bizuser/check-user-status?id=${userId}`, (res) => {
      const lastLogin = res.data.lastLogin
      if (lastLogin) {
        const $login = $('.J_loginOn')
        renderRbcomp(<DateShow date={lastLogin[0]} />, $login[0])

        const ip = lastLogin[1]
        $.get(`/commons/ip-location?ip=${ip}`, (res) => {
          let L = ip
          if (res.error_code === 0 && res.data.country !== 'N') {
            L = res.data.country === 'R' ? $L('局域网') : [res.data.region, res.data.country].join(', ')
          }
          $(`<span class="ml-1" title="${ip}">(${L})</span>`).appendTo($login)
        })
      }

      if (res.data.system === true) {
        // v35
        if (userId === '001-0000000000000000') {
          $('.view-action').remove()
        } else {
          $('.J_mores .dropdown-menu>*').each(function () {
            if (!$(this).hasClass('J_resetpwd')) $(this).remove()
          })
        }

        $('.J_tips').removeClass('hide').find('.message p').text($L('系统内置超级管理员。此用户拥有最高级系统权限，请谨慎使用'))
        return
      }

      $('.J_changeRole').on('click', () => renderRbcomp(<DlgEnableUser user={userId} roleSet={true} role={res.data.role} roleAppends={res.data.roleAppends} />))
      $('.J_changeDept').on('click', () => renderRbcomp(<DlgEnableUser user={userId} deptSet={true} dept={res.data.dept} />))

      if (res.data.disabled === true) {
        $('.J_disable').remove()

        if (!res.data.role || !res.data.dept) {
          $('.J_enable')
            .off('click')
            .on('click', () =>
              renderRbcomp(<DlgEnableUser user={userId} enable={true} roleSet={!res.data.role} role={res.data.role} roleAppends={res.data.roleAppends} deptSet={!res.data.dept} dept={res.data.dept} />)
            )
        }
      } else {
        $('.J_enable').remove()
      }

      if (!res.data.active) {
        const reasons = []
        if (!res.data.role) reasons.push($L('未指定角色'))
        else if (res.data.roleDisabled) reasons.push($L('所属角色已禁用'))
        if (!res.data.dept) reasons.push($L('未指定部门'))
        else if (res.data.deptDisabled) reasons.push($L('所属部门已禁用'))
        if (res.data.disabled === true) reasons.push($L('已禁用'))
        $('.J_tips')
          .removeClass('hide')
          .find('.message p')
          .text($L('当前用户处于未激活状态，因为其 %s', reasons.join(' / ')))
      }

      if (res.data.roleAppends && res.data.roleAppends.length > 0) {
        $.get(`/commons/search/read-labels?ids=${res.data.roleAppends.join(',')}`, (res) => {
          const $p = $('.J_roleAppends').empty()
          for (let k in res.data) {
            $(
              `<a class="badge badge-info fs-12 up-2" href="${rb.baseUrl}/admin/bizuser/role/${k}" target="_blank" title="${$L('查看角色权限')}"><i class="icon mdi mdi-account-lock"></i> ${
                res.data[k]
              }</a>`
            ).appendTo($p)
          }
        })
      }
    })
  }

  // v3.8
  if (userId === '001-0000000000000001') {
    const RbForm_renderAfter = RbForm.renderAfter
    RbForm.renderAfter = function (formObject) {
      typeof RbForm_renderAfter === 'function' && RbForm_renderAfter()

      formObject.onFieldValueChange((nv) => {
        if (nv.name === 'isDisabled') {
          let c = formObject.getFieldComp('isDisabled') || {}
          if (nv.value === 'T') c.setTip(<span className="text-warning">{$L('禁用将导致超级管理员无法登录')}</span>)
          else c.setTip(null)
        }
      })
    }
  }
})

// 启用/禁用
const toggleDisabled = function (disabled, _alert) {
  _alert && _alert.disabled(true)

  const data = {
    user: userId,
    enable: !disabled,
  }

  $.post('/admin/bizuser/enable-user', JSON.stringify(data), (res) => {
    if (res.error_code === 0) {
      RbHighbar.success(disabled ? $L('用户已禁用') : $L('用户已启用'))
      _reload(200)
    } else {
      RbHighbar.error(res.error_msg)
      _alert && _alert.disabled()
    }
  })
}

// 删除用户
const deleteUser = function (id, _alert) {
  _alert && _alert.disabled(true)

  $.post(`/admin/bizuser/user-delete?id=${id}`, (res) => {
    if (res.error_code === 0) {
      parent.location.hash = '!/View/'
      parent.location.reload()
    } else {
      RbHighbar.error(res.error_msg)
      _alert && _alert.disabled()
    }
  })
}

// 激活用户/变更部门/角色
class DlgEnableUser extends RbModalHandler {
  constructor(props) {
    super(props)

    if (props.enable) this._title = $L('激活用户')
    else this._title = props.deptSet ? $L('修改部门') : $L('修改角色')
  }

  render() {
    return (
      <RbModal title={this._title} ref={(c) => (this._dlg = c)} disposeOnHide className="sm-height">
        <div className="form">
          {this.props.deptSet && (
            <div className="form-group row">
              <label className="col-sm-3 col-form-label text-sm-right">{$L('选择所属部门')}</label>
              <div className="col-sm-7">
                <UserSelector hideUser={true} hideRole={true} hideTeam={true} multiple={false} defaultValue={this.props.dept} ref={(c) => (this._deptNew = c)} />
              </div>
            </div>
          )}
          {this.props.roleSet && (
            <React.Fragment>
              <div className="form-group row">
                <label className="col-sm-3 col-form-label text-sm-right">{$L('选择角色')}</label>
                <div className="col-sm-7">
                  <UserSelector hideUser={true} hideDepartment={true} hideTeam={true} multiple={false} defaultValue={this.props.role} ref={(c) => (this._roleNew = c)} />
                </div>
              </div>
              <div className="form-group row">
                <label className="col-sm-3 col-form-label text-sm-right">
                  {$L('附加角色')} {$L('(可选)')} <sup className="rbv" />
                </label>
                <div className="col-sm-7">
                  <UserSelector hideUser={true} hideDepartment={true} hideTeam={true} defaultValue={this.props.roleAppends} ref={(c) => (this._roleAppends = c)} />
                  <p className="form-text">{$L('选择的多个角色权限将被合并，高权限优先')}</p>
                </div>
              </div>
            </React.Fragment>
          )}
          <div className="form-group row footer">
            <div className="col-sm-7 offset-sm-3" ref={(c) => (this._btns = c)}>
              <button className="btn btn-primary" type="button" onClick={() => this.post()}>
                {$L('确定')}
              </button>
              <button type="button" className="btn btn-link" onClick={() => this.hide()}>
                {$L('取消')}
              </button>
            </div>
          </div>
        </div>
      </RbModal>
    )
  }

  post() {
    const data = { user: this.props.user }

    if (this.props.enable === true) {
      data.enable = true
    }
    if (this._deptNew) {
      const v = this._deptNew.val()
      if (v.length === 0) return RbHighbar.create($L('请选择部门'))
      data.dept = v[0]
    }
    if (this._roleNew) {
      const v = this._roleNew.val()
      if (v.length === 0) return RbHighbar.create($L('请选择角色'))
      data.role = v[0]
    }
    if (this._roleAppends) {
      data.roleAppends = this._roleAppends.val().join(',')
      if (data.roleAppends && rb.commercial < 1) {
        RbHighbar.error(WrapHtml($L('免费版不支持附加角色功能 [(查看详情)](https://getrebuild.com/docs/rbv-features)')))
        return
      }
    }

    const $btn = $(this._btns).find('.btn').button('loading')
    $.post('/admin/bizuser/enable-user', JSON.stringify(data), (res) => {
      if (res.error_code === 0) {
        if (data.enable === true) RbHighbar.success($L('用户已激活'))
        _reload(data.enable ? 200 : 0)
      } else {
        $btn.button('reset')
        RbHighbar.error(res.error_msg)
      }
    })
  }
}

const _reload = function (timeout) {
  setTimeout(() => RbViewPage.reload(), timeout || 1)
  parent && parent.RbListPage && parent.RbListPage.reload()
}
